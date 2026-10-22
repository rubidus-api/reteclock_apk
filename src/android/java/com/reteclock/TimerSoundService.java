package com.reteclock;

import android.app.AlarmManager;
import android.app.Notification;
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

import com.reteclock.core.TimerCueLedger;
import com.reteclock.core.TimerCues;
import com.reteclock.core.TimerMemory;
import com.reteclock.core.TimerPreset;
import com.reteclock.core.TimerRun;
import com.reteclock.core.Tones;

import java.util.List;

/**
 * The one player of the timer's cues, for the whole of a run (R130, R145).
 *
 * <p>It used to stand in only while the screen was off, with a hand-over each way, and the
 * hand-overs were where cues were lost and played twice: the clock's pause and the screen-off
 * broadcast both handed the run over, the second with an older moment, and the count-in was heard
 * again. Now the screens only draw and flash; they write the run down as they always did and call
 * {@link #sync}, and this reads it back. Up to where it has played is a {@link TimerCueLedger},
 * stored beside the run, so reading it again plays nothing again.
 *
 * <p>Between cues the phone may sleep. The next cue is waited for twice: by a handler under a partial
 * wake lock, and — when it is {@link #ALARM_LEAD_MS} or more away — by an exact alarm clock, which
 * Doze and the battery savers let through and which starts this again if its process was killed.
 * Whichever comes first plays the cue; the other finds nothing left to play.
 *
 * <p>A foreground service, because Android 8 and up stop a background one within a minute, and so
 * with a notification: the interval, when it ends, *Pause* (*Resume* while paused) and *Stop*
 * (owner's choice, 2026-10-03). At the finish it plays the finish, leaves a notification saying so
 * — what a phone on silent shows instead of a sound — and goes; the screen is not turned on.
 */
public class TimerSoundService extends Service implements TimerView.CueSink {

    static final String CHANNEL = "timer";
    private static final int ID = 81;
    private static final int FINISHED_ID = 82;

    private static final String ACTION_SYNC = "com.reteclock.timer.SYNC";
    private static final String ACTION_ALARM = "com.reteclock.timer.ALARM";
    private static final String ACTION_PAUSE = "com.reteclock.timer.PAUSE";
    private static final String ACTION_RESUME = "com.reteclock.timer.RESUME";
    private static final String ACTION_STOP = "com.reteclock.timer.STOP";
    private static final int REQUEST_ALARM = 24;
    private static final int REQUEST_SHOW = 25;

    /** How long the finish is given to play before the service lets go of it. */
    private static final long FINISH_GRACE_MS = 60_000L;
    /** A little past each cue, so the wake lock outlasts the sound it was taken for. */
    private static final long WAKE_SLACK_MS = 10_000L;
    /** A cue this far away or more is also waited for by an alarm; a tick every second is not. */
    static final long ALARM_LEAD_MS = 5_000L;

    // Shared with the screens in the same process.
    /** False once the platform refused to start this; the screens then play the cues themselves. */
    private static boolean usable = true;
    /** Whether the run written down was changed here (Pause, Resume, Stop) since a screen looked. */
    private static boolean changed;
    /** The screen to tell at once when that happens; null when none is in front. */
    private static Runnable onChanged;
    /** Whether the screensaver is showing: it does not speak messages (R130). */
    private static boolean dreaming;

    private final Handler handler = new Handler();
    private final SoundPlayer player = new SoundPlayer();
    private TimerSounds tones;
    private TimerVoice voice;
    private PowerManager.WakeLock wakeLock;
    private TimerRun run;
    private String runKey = "";
    private TimerCueLedger ledger = TimerCueLedger.NONE;
    private long startEpochMs;
    private boolean finishing;

    /**
     * The run written down has changed, or a screen has come back to one: have it played. Nothing
     * written down and nothing playing is nothing to do.
     */
    static void sync(Context context) {
        if (Settings.runOrigin(context) == TimerMemory.NONE && !running) {
            return;
        }
        Intent intent = new Intent(context, TimerSoundService.class).setAction(ACTION_SYNC);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                WakeApi26.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
            usable = true;
        } catch (RuntimeException e) {
            // The platform refused: the screens play the cues while they show, as they used to.
            usable = false;
        }
    }

    /** Whether this plays the cues; when not, a screen showing the timer plays them itself. */
    static boolean plays() {
        return usable;
    }

    /** Whether Pause, Resume or Stop on the notification changed the run since this was asked. */
    static boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    /** The screen in front, told when the notification changes the run; null to stop telling. */
    static void setOnChanged(Runnable listener) {
        onChanged = listener;
    }

    static void setDreaming(boolean showing) {
        dreaming = showing;
    }

    /** Whether an instance is alive in this process; a stop must still reach it. */
    private static boolean running;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        tones = new TimerSounds(this);
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reteclock:timer");
            wakeLock.setReferenceCounted(false);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            WakeApi26.createQuietChannel(this, CHANNEL, getString(R.string.timer_locked_channel),
                    getString(R.string.timer_locked_channel_note));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        long now = SystemClock.elapsedRealtime();
        if (run == null) {
            read();
        }
        // Foreground first, whatever follows: Android 8 and up stop a service that was started to
        // be foreground and did not become so within seconds.
        startForeground(ID, notification(now));
        if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_RESUME.equals(action)) {
            resume();
        } else if (ACTION_STOP.equals(action)) {
            stopRun();
        } else {
            // A screen's sync, the alarm, or the platform starting this again after a kill.
            begin();
        }
        return START_STICKY;
    }

    /** The run as written down, and up to where it was played. */
    private void read() {
        run = storedRun();
        runKey = TimerCueLedger.keyOf(Settings.runPreset(this), Settings.runStarted(this));
        startEpochMs = Settings.runStarted(this);
        ledger = Settings.timerCueLedger(this);
    }

    private void begin() {
        read();
        finishing = false;
        handler.removeCallbacks(stepper);
        handler.removeCallbacks(finishWhenQuiet);
        long now = SystemClock.elapsedRealtime();
        if (run == null) {
            finish();
            return;
        }
        if (!ledger.isOf(runKey)) {
            // A run not played before: whatever said the last one had finished is out of date.
            cancelFinished();
        }
        if (run.isPaused()) {
            cancelAlarm();
            release();
            player.fadeOutAndStop();
            refreshNotification(now);
            return;
        }
        if (run.finishedAt(now) && ledger.isOf(runKey) && ledger.mark() >= run.totalMs()) {
            // Its finish has been played already; this is a screen still showing the full bar.
            finish();
            return;
        }
        step();
    }

    /** The run the screens share, as written down. */
    private TimerRun storedRun() {
        List<TimerPreset> presets = Settings.timerPresets(this);
        if (presets.isEmpty()) {
            return null;
        }
        return TimerMemory.restore(presets.get(Settings.timerChosen(this)),
                Settings.runPreset(this), Settings.runOrigin(this), Settings.runPausedAt(this),
                SystemClock.elapsedRealtime());
    }

    /** Plays what has fallen due, and sleeps until the next cue. */
    private final Runnable stepper = new Runnable() {
        @Override
        public void run() {
            step();
        }
    };

    private void step() {
        handler.removeCallbacks(stepper);
        if (run == null || run.isPaused() || finishing) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        List<TimerCues.Cue> cues = TimerCues.between(run, ledger.windowFrom(runKey, run, now), now);
        ledger = ledger.playedTo(runKey, run, now);
        Settings.setTimerCueLedger(this, ledger);
        boolean audible = audible();
        if (audible) {
            TimerView.dispatch(run.preset(), cues, this);
        }
        for (TimerCues.Cue cue : cues) {
            // What was played, for the device lanes (verify-timer-owner.sh): one line per cue, so
            // a cue played twice or not at all shows; on some images the emulator's sound file
            // cannot say when a sound came.
            android.util.Log.d("reteclock", "timer cue: " + cue.kind + " " + cue.interval + " "
                    + cue.atMs + (audible ? "" : " quiet"));
        }
        if (run.finishedAt(now)) {
            finishing = true;
            cancelAlarm();
            if (hasFinish(cues)) {
                showFinished();
            }
            hold(FINISH_GRACE_MS);
            handler.postDelayed(finishWhenQuiet, 1000L);
            return;
        }
        long next = TimerCues.nextAt(run, now);
        if (next == TimerCues.NONE) {
            finish();
            return;
        }
        long delay = Math.max(0L, next - now);
        hold(delay + WAKE_SLACK_MS);
        if (delay >= ALARM_LEAD_MS) {
            setAlarm(delay);
        } else {
            cancelAlarm();
        }
        refreshNotification(now);
        handler.postDelayed(stepper, delay);
    }

    private static boolean hasFinish(List<TimerCues.Cue> cues) {
        for (TimerCues.Cue cue : cues) {
            if (cue.kind == TimerCues.FINISH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the cues are heard just now. A ringing wake bell has the right of way (F4); with the
     * screen off they are heard only if Timer settings says so — the run keeps its time either way.
     */
    private boolean audible() {
        if (WakeRingService.isRinging() || BellRinger.anyWakeActive()) {
            return false;
        }
        return ClockActivity.screenOn(this) || Settings.timerWhileLocked(this);
    }

    /** After the finish: wait for its sound to end, then go. */
    private final Runnable finishWhenQuiet = new Runnable() {
        @Override
        public void run() {
            if (player.isPlaying() && finishing) {
                handler.postDelayed(this, 1000L);
                return;
            }
            finish();
        }
    };

    private void pause() {
        if (run == null || run.isPaused()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        run = run.pausedAt(now);
        remember();
        handler.removeCallbacks(stepper);
        cancelAlarm();
        release();
        player.fadeOutAndStop();
        refreshNotification(now);
        told();
    }

    private void resume() {
        if (run == null || !run.isPaused()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        run = run.resumedAt(now);
        remember();
        told();
        step();
    }

    /** Stop from the notification: the run ends, as the strip's own stop ends it. */
    private void stopRun() {
        if (run != null) {
            long now = SystemClock.elapsedRealtime();
            long elapsed = run.rawElapsedAt(now);
            if (!run.preset().loops) {
                elapsed = Math.min(elapsed, run.totalMs());
            }
            // Stopped during the count-in, or as good as: nothing worth writing down — the strip's
            // own rule.
            if (elapsed >= 1000L) {
                ClockActivity.logRun(this, run, startEpochMs, elapsed);
            }
        }
        run = null;
        Settings.forgetRun(this);
        player.fadeOutAndStop();
        told();
        finish();
    }

    /** The screen in front, if any, takes up the run as it now stands. */
    private void told() {
        changed = true;
        Runnable listener = onChanged;
        if (listener != null) {
            listener.run();
        }
    }

    private void remember() {
        Settings.rememberRun(this, TimerMemory.identityOf(run.preset()),
                TimerMemory.originOf(run, SystemClock.elapsedRealtime()),
                TimerMemory.pausedAtOf(run), startEpochMs);
    }

    private void hold(long ms) {
        if (wakeLock != null) {
            wakeLock.acquire(ms);
        }
    }

    private void release() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    private void finish() {
        handler.removeCallbacksAndMessages(null);
        cancelAlarm();
        release();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        player.stopNow();
        if (voice != null) {
            voice.release();
            voice = null;
        }
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- the alarm that wakes the phone for the next cue ---------------------------------------

    private PendingIntent alarmIntent(int flags) {
        Intent intent = new Intent(this, TimerSoundService.class).setAction(ACTION_ALARM);
        if (Build.VERSION.SDK_INT >= 26) {
            return WakeApi26.foregroundService(this, REQUEST_ALARM, intent, flags);
        }
        return PendingIntent.getService(this, REQUEST_ALARM, intent, flags);
    }

    /** An exact alarm {@code delayMs} from now; the one before it is replaced. */
    private void setAlarm(long delayMs) {
        try {
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) {
                return;
            }
            long at = System.currentTimeMillis() + delayMs;
            PendingIntent operation = alarmIntent(PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= 21) {
                // An alarm clock: exempt from Doze and let through by the battery savers. The
                // status bar shows it while a timer runs (owner, 2026-10-22).
                PendingIntent show = PendingIntent.getActivity(this, REQUEST_SHOW,
                        new Intent(this, ClockActivity.class).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT);
                WakeApi21.setAlarmClock(alarms, at, operation, show);
            } else if (Build.VERSION.SDK_INT >= 19) {
                alarms.setExact(AlarmManager.RTC_WAKEUP, at, operation);
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, at, operation);
            }
        } catch (RuntimeException e) {
            // No alarm: the handler and the wake lock still wait for the cue, as they always did.
        }
    }

    private void cancelAlarm() {
        try {
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            // NO_CREATE: asking to cancel must not itself leave a pending intent with the system.
            PendingIntent held = alarmIntent(PendingIntent.FLAG_NO_CREATE);
            if (alarms != null && held != null) {
                alarms.cancel(held);
                held.cancel();
            }
        } catch (RuntimeException e) {
            // Nothing held, or nothing to ask.
        }
    }

    // ---- what a cue becomes: the clock's own rules ----------------------------------------------

    @Override
    public void cue(Tones.Note[] pattern) {
        CueSound.cue(this, tones, pattern);
    }

    @Override
    public void sound(String name, Tones.Note[] fallback) {
        CueSound.play(this, player, tones, name, fallback);
    }

    @Override
    public void speak(String message) {
        // Left to the clock on the screensaver: a screensaver that starts talking is harder to
        // explain than one that beeps.
        if (message == null || message.isEmpty() || !CueSound.canSpeak(this)
                || (dreaming && ClockActivity.screenOn(this))) {
            return;
        }
        if (voice == null) {
            voice = new TimerVoice(this);
            voice.setStream(AudioManager.STREAM_ALARM);
        }
        voice.say(message, SystemClock.elapsedRealtime(),
                Settings.soundGain(this, com.reteclock.core.SoundLevels.TIMER_MESSAGES));
    }

    @Override
    public void flash() {
        // The screens flash for themselves.
    }

    // ---- the notifications ----------------------------------------------------------------------

    private void refreshNotification(long now) {
        if (finishing) {
            return;
        }
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(ID, notification(now));
            }
        } catch (RuntimeException e) {
            // A notification that could not be updated still names the run; nothing else is lost.
        }
    }

    /** "<preset> finished", left behind when the service goes: seen on a phone that made no sound. */
    @SuppressWarnings("deprecation")
    private void showFinished() {
        if (run == null) {
            return;
        }
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) {
                return;
            }
            String title = getString(R.string.timer_finished, run.preset().name);
            String text = android.text.format.DateFormat.getTimeFormat(this)
                    .format(new java.util.Date());
            Notification done;
            if (Build.VERSION.SDK_INT < 11) {
                done = new Notification(R.drawable.ic_launcher_monochrome, title,
                        System.currentTimeMillis());
                done.setLatestEventInfo(this, title, text, openClock());
                done.flags |= Notification.FLAG_AUTO_CANCEL;
            } else {
                Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                        ? WakeApi26.builder(this, CHANNEL)
                        : new Notification.Builder(this);
                builder.setSmallIcon(R.drawable.ic_launcher_monochrome)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setContentIntent(openClock());
                done = Build.VERSION.SDK_INT >= 16 ? builder.build() : builder.getNotification();
            }
            manager.notify(FINISHED_ID, done);
        } catch (RuntimeException e) {
            // The finish was played as the Volume card says; only the note of it is missing.
        }
    }

    private void cancelFinished() {
        try {
            android.app.NotificationManager manager = (android.app.NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(FINISHED_ID);
            }
        } catch (RuntimeException e) {
            // Nothing shown.
        }
    }

    private PendingIntent openClock() {
        return PendingIntent.getActivity(this, 20,
                new Intent(this, ClockActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SuppressWarnings("deprecation")
    private Notification notification(long now) {
        String title = run == null ? getString(R.string.app_name) : run.preset().name;
        String text;
        if (run == null) {
            text = getString(R.string.timer_locked_running);
        } else if (run.isPaused()) {
            text = getString(R.string.timer_locked_paused);
        } else {
            String interval = run.intervalObjectAt(now) == null ? "" : run.intervalObjectAt(now).name;
            long endsAt = System.currentTimeMillis() + Math.max(0L, run.remainingInIntervalAt(now));
            text = getString(R.string.timer_locked_ends, interval,
                    android.text.format.DateFormat.getTimeFormat(this)
                            .format(new java.util.Date(endsAt)));
        }
        PendingIntent open = openClock();
        if (Build.VERSION.SDK_INT < 11) {
            Notification old = new Notification(R.drawable.ic_launcher_monochrome, title,
                    System.currentTimeMillis());
            old.setLatestEventInfo(this, title, text, open);
            old.flags |= Notification.FLAG_ONGOING_EVENT;
            return old;
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? WakeApi26.builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open);
        if (Build.VERSION.SDK_INT >= 16) {
            if (run != null) {
                boolean paused = run.isPaused();
                builder.addAction(0, getString(paused
                        ? R.string.timer_locked_resume : R.string.timer_locked_pause),
                        action(paused ? ACTION_RESUME : ACTION_PAUSE, paused ? 22 : 21));
                builder.addAction(0, getString(R.string.timer_locked_stop), action(ACTION_STOP, 23));
            }
            return builder.build();
        }
        return builder.getNotification();
    }

    private PendingIntent action(String name, int request) {
        Intent intent = new Intent(this, TimerSoundService.class);
        intent.setAction(name);
        return PendingIntent.getService(this, request, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
