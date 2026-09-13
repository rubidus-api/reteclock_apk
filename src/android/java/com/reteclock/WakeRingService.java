package com.reteclock;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import com.reteclock.core.Bell;
import com.reteclock.core.Tones;
import com.reteclock.core.WakeLog;
import com.reteclock.core.WakePutOff;
import com.reteclock.core.WakeSchedule;

import java.io.File;

/**
 * Rings a bell that woke the phone while no clock was on screen (RFC-0012).
 *
 * <p>It exists only while a bell is sounding: started by {@link WakeReceiver}, it goes foreground at
 * once, plays the bell on the alarm stream, shows the card, and stops itself when the bell is
 * answered or nobody answers it. Nothing of it runs between one ring and the next. Shipped disabled.
 *
 * <p><b>The clock's own bells come first.</b> If one is sounding in this process — on a screensaver,
 * say — the wake ring waits for it to end, for up to {@link WakeBells#WAIT_FOR_BELL_MS}, before it
 * makes a sound or opens the card; while it rings, the clock's tick passes its own bells over, so two
 * bells never sound at once.
 */
public class WakeRingService extends Service {

    static final String ACTION_RING = "com.reteclock.action.RING";
    static final String ACTION_PUT_OFF = "com.reteclock.action.PUT_OFF";
    static final String ACTION_STOP = "com.reteclock.action.STOP";
    static final String ACTION_UNANSWERED = "com.reteclock.action.UNANSWERED";

    private static final int ID_WAITING = 71;
    private static final int ID_RINGING = 72;
    private static final int CHIME_GAP_MS = 350;
    private static final long WAKE_LOCK_MS = 3 * 60_000L;

    private static PowerManager.WakeLock wakeLock;
    private static WakeRingService running;

    private final Handler handler = new Handler();
    private final SoundPlayer player = new SoundPlayer();
    private TimerSounds chime;

    private Bell bell;
    private long due;
    private boolean locked;
    private boolean sounding;
    private long arrivedAt;

    /** Starts the service for a wake-up that has arrived, holding the phone awake until it is up. */
    static void start(Context context, long due, boolean locked) {
        acquire(context);
        Intent ring = new Intent(context, WakeRingService.class);
        ring.setAction(ACTION_RING);
        ring.putExtra(WakeBells.EXTRA_DUE, due);
        ring.putExtra(WakeBells.EXTRA_LOCKED, locked);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                WakeApi26.startForegroundService(context, ring);
            } else {
                context.startService(ring);
            }
        } catch (RuntimeException e) {
            release();
            WakeBells.record(context, WakeLog.MISSED, due, "");
        }
    }

    /** A bell a clock screen had taken and could not ring before it went; already resolved. */
    private static Bell handoffBell;
    private static long handoffDue;

    /** Rings a bell the clock had already accepted, when its screen goes before it could. */
    static void startWith(Context context, Bell bell, long due) {
        handoffBell = bell;
        handoffDue = due;
        start(context, due, false);
    }

    /** Whether a wake bell is ringing here now — what the clock's tick gives way to. */
    static boolean isRinging() {
        return running != null && running.bell != null;
    }

    /** The bell being rung, for the card; null when there is none. */
    static Bell ringingBell() {
        return running == null ? null : running.bell;
    }

    private static synchronized void acquire(Context context) {
        if (wakeLock == null) {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power == null) {
                return;
            }
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reteclock:wake");
            wakeLock.setReferenceCounted(false);
        }
        wakeLock.acquire(WAKE_LOCK_MS);
    }

    private static synchronized void release() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        chime = new TimerSounds(this);
        player.setStream(AudioManager.STREAM_ALARM);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RING.equals(action)) {
            // Foreground first, whatever follows: Android 8 and up stop a service that was started to
            // be foreground and did not become so within seconds. A service already ringing keeps
            // the notification it is ringing under.
            if (bell != null && sounding) {
                startForeground(ID_RINGING, notification(true));
            } else {
                startForeground(ID_WAITING, notification(false));
            }
            arrived(intent.getLongExtra(WakeBells.EXTRA_DUE, 0L),
                    intent.getBooleanExtra(WakeBells.EXTRA_LOCKED, false));
        } else if (ACTION_PUT_OFF.equals(action)) {
            answer(WakeLog.PUT_OFF);
        } else if (ACTION_STOP.equals(action)) {
            answer(WakeLog.STOPPED);
        } else if (ACTION_UNANSWERED.equals(action)) {
            answer(WakeLog.UNANSWERED);
        } else if (bell == null) {
            finish();
        }
        return START_NOT_STICKY;
    }

    private void arrived(long when, boolean onLockedPhone) {
        boolean lockedNow = onLockedPhone || !WakeBells.isUnlocked(this);
        if (bell != null) {
            // One is already ringing. The second is marked dealt with and the held wake-up moves on;
            // the person is already awake and answering the first.
            WakeBells.handled(this, when, lockedNow);
            return;
        }
        if (handoffBell != null && handoffDue == when) {
            // Resolved and marked dealt with by the screen that took it; rung as it stands.
            bell = handoffBell;
            handoffBell = null;
            due = when;
            locked = false;
            arrivedAt = SystemClock.uptimeMillis();
            waitThenRing.run();
            return;
        }
        Bell found = lockedNow ? WakeBells.resolveLocked(this, when) : WakeBells.resolve(this, when);
        if (found == null) {
            afterwards(lockedNow);
            finish();
            return;
        }
        WakeBells.handled(this, when, lockedNow);
        if (!WakeSchedule.worthRinging(when, System.currentTimeMillis())) {
            WakeBells.record(this, WakeLog.LATE, when, found.label);
            afterwards(lockedNow);
            finish();
            return;
        }
        bell = found;
        due = when;
        locked = lockedNow;
        arrivedAt = SystemClock.uptimeMillis();
        waitThenRing.run();
    }

    private final Runnable waitThenRing = new Runnable() {
        @Override
        public void run() {
            if (bell == null) {
                return;
            }
            if (BellRinger.anySounding()
                    && SystemClock.uptimeMillis() - arrivedAt < WakeBells.WAIT_FOR_BELL_MS) {
                handler.postDelayed(this, 500L);
                return;
            }
            ring();
        }
    };

    private void ring() {
        sounding = true;
        WakeBells.record(this, locked ? WakeLog.LOCKED : WakeLog.RANG, due, bell.label);
        File file = locked || bell.sound.isEmpty() ? null : Settings.sounds(this).file(bell.sound);
        if (file == null) {
            chime.playAlarm(Tones.repeated(Tones.CHIME, bell.repeats, CHIME_GAP_MS));
        } else {
            player.play(file, Settings.soundClips(this).of(bell.sound), bell.repeats);
        }
        // A fresh notification, under its own id, so the platform treats it as new and shows the
        // card full screen rather than quietly updating the one that was waiting.
        startForeground(ID_RINGING, notification(true));
        cancelNotification(ID_WAITING);
        if (Build.VERSION.SDK_INT <= 28) {
            // Below Android 10 an app may still open a window from the background, and does.
            Intent card = new Intent(this, WakeCardActivity.class);
            card.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            try {
                startActivity(card);
            } catch (RuntimeException ignored) {
                // The notification is still there to answer.
            }
        }
        // The card closes itself after a minute; this is the same minute for a phone whose card
        // never appeared, so the service never rings on for ever.
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                answer(WakeLog.UNANSWERED);
            }
        }, BellCard.UNANSWERED_MS + 2_000L);
    }

    private void answer(String kind) {
        if (bell == null) {
            finish();
            return;
        }
        Bell answered = bell;
        boolean wasLocked = locked;
        if (sounding) {
            WakeBells.record(this, kind, due, answered.label);
        }
        if (WakeLog.PUT_OFF.equals(kind) && !wasLocked) {
            Settings.setWakePutOff(this, WakePutOff.of(answered, System.currentTimeMillis()));
        }
        bell = null;
        afterwards(wasLocked);
        finish();
    }

    private void afterwards(boolean wasLocked) {
        if (wasLocked) {
            WakeBells.rearmLocked(this);
        } else {
            WakeBells.rearm(this);
        }
    }

    private void finish() {
        handler.removeCallbacksAndMessages(null);
        player.stopNow();
        sounding = false;
        bell = null;
        WakeCardActivity.close();
        stopForeground(true);
        stopSelf();
        release();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        player.stopNow();
        if (running == this) {
            running = null;
        }
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- the notification ------------------------------------------------------------------

    private Notification notification(boolean ringing) {
        String title = bell == null ? getString(R.string.wake_notification_waiting)
                : String.format("%02d:%02d", bell.hour(), bell.minute());
        String text = bell == null ? getString(R.string.app_name)
                : bell.label.isEmpty() ? getString(R.string.bell_card_untitled) : bell.label;
        PendingIntent card = PendingIntent.getActivity(this, 3,
                new Intent(this, WakeCardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT < 11) {
            return oldNotification(title, text, card, ringing);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? WakeApi26.builder(this, WakeBells.CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(card);
        if (ringing) {
            builder.setFullScreenIntent(card, true);
        }
        if (Build.VERSION.SDK_INT >= 16) {
            builder.setPriority(Notification.PRIORITY_MAX);
            if (ringing && bell != null) {
                if (bell.canSnooze()) {
                    builder.addAction(0, getString(R.string.bell_card_put_off, bell.snoozeMinutes),
                            serviceIntent(ACTION_PUT_OFF, 10));
                }
                builder.addAction(0, getString(R.string.bell_card_stop),
                        serviceIntent(ACTION_STOP, 11));
            }
            return builder.build();
        }
        return builder.getNotification();
    }

    @SuppressWarnings("deprecation")
    private Notification oldNotification(String title, String text, PendingIntent card,
            boolean ringing) {
        Notification old = new Notification(R.drawable.ic_launcher_monochrome, title,
                System.currentTimeMillis());
        old.setLatestEventInfo(this, title, text, card);
        old.flags |= Notification.FLAG_ONGOING_EVENT;
        if (ringing) {
            old.fullScreenIntent = card;
        }
        return old;
    }

    private PendingIntent serviceIntent(String action, int request) {
        Intent intent = new Intent(this, WakeRingService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, request, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void cancelNotification(int id) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(id);
        }
    }
}
