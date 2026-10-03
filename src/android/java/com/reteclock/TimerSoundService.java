package com.reteclock;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import com.reteclock.core.TimerCues;
import com.reteclock.core.TimerMemory;
import com.reteclock.core.TimerPreset;
import com.reteclock.core.TimerRun;
import com.reteclock.core.Tones;

import java.util.List;

/**
 * The timer's cues with the screen off or locked (R130).
 *
 * <p>With the screen on, the strip looks every frame and plays whatever fell between two looks. With
 * it off nothing looks, so the timer went quiet while its time ran on. This stands in: the screen
 * that went dark hands over the moment up to which it had played, and this plays each cue at its own
 * time — the same cues, through the same {@link TimerView#dispatch}, as loud as the Volume card says
 * for the phone's mode. Between cues the phone sleeps: a partial wake lock is held only until the
 * next cue is due ({@link TimerCues#nextAt}).
 *
 * <p>A foreground service, because Android 8 and up stop a background one within a minute, and so
 * with a notification: the interval, when it ends, *Pause* (*Resume* while paused) and *Stop* (owner's
 * choice, 2026-10-03). Those two change the run that is written down ({@link Settings#rememberRun}),
 * which is the run the clock picks up when it comes back — see {@link #takeBack}.
 *
 * <p>At the finish it plays the finish and goes; the screen is not turned on (owner's choice).
 */
public class TimerSoundService extends Service implements TimerView.CueSink {

    /** {@link #takeBack}'s answer when nothing was handed over. */
    static final long NOTHING = Long.MIN_VALUE;

    static final String CHANNEL = "timer";
    private static final int ID = 81;

    private static final String ACTION_PAUSE = "com.reteclock.timer.PAUSE";
    private static final String ACTION_RESUME = "com.reteclock.timer.RESUME";
    private static final String ACTION_STOP = "com.reteclock.timer.STOP";

    /** How long the finish is given to play before the service lets go of it. */
    private static final long FINISH_GRACE_MS = 60_000L;
    /** A little past each cue, so the wake lock outlasts the sound it was taken for. */
    private static final long WAKE_SLACK_MS = 10_000L;

    // Shared with the screens in the same process: the hand-over is a few numbers, not a message.
    /** Whether a screen still wants the cues played; false once one has taken them back. */
    private static boolean wanted;
    /** Whether the service has been asked since the last {@link #takeBack}. */
    private static boolean used;
    /** Up to when cues have been played, by the screen that handed over or by the service since. */
    private static long playedUpTo = NOTHING;

    private final Handler handler = new Handler();
    private final SoundPlayer player = new SoundPlayer();
    private TimerSounds tones;
    private TimerVoice voice;
    private PowerManager.WakeLock wakeLock;
    private TimerRun run;
    private long startEpochMs;
    private boolean finishing;

    /**
     * Called by a screen going dark with a run going: carry on from {@code lastCueMs}.
     *
     * Asked only when the screen is really off — leaving the clock for its settings, or for another
     * app, goes on as it always did.
     */
    static void startFor(Context context, long lastCueMs) {
        wanted = true;
        used = true;
        playedUpTo = lastCueMs;
        Intent intent = new Intent(context, TimerSoundService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                WakeApi26.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            // The platform refused; the timer is quiet with the screen off, as it used to be.
            wanted = false;
        }
    }

    /**
     * The screen is back. Stops the service and answers up to when cues were played, or
     * {@link #NOTHING} if it was never asked — in which case the screen goes on as before.
     *
     * When it was asked, the run may have been paused or stopped from the notification, so the
     * screen takes the run that is written down rather than the one it was holding.
     */
    static long takeBack(Context context) {
        wanted = false;
        if (!used) {
            return NOTHING;
        }
        used = false;
        long played = playedUpTo;
        playedUpTo = NOTHING;
        context.stopService(new Intent(context, TimerSoundService.class));
        // Never NOTHING once used: "asked, nothing played" still means the run must be re-read.
        return played == NOTHING ? SystemClock.elapsedRealtime() : played;
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
        boolean answer = ACTION_PAUSE.equals(action) || ACTION_RESUME.equals(action)
                || ACTION_STOP.equals(action);
        if (answer && run == null && !ACTION_STOP.equals(action)) {
            // A button on a notification the service no longer stands behind: nothing to answer.
            finish();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_RESUME.equals(action)) {
            resume();
        } else if (ACTION_STOP.equals(action)) {
            stopRun();
        } else {
            begin();
        }
        return START_NOT_STICKY;
    }

    private void begin() {
        run = storedRun();
        startEpochMs = Settings.runStarted(this);
        long now = SystemClock.elapsedRealtime();
        // Foreground first, whatever follows: Android 8 and up stop a service that was started to
        // be foreground and did not become so within seconds.
        startForeground(ID, notification(now));
        if (!wanted || run == null || run.isPaused() || run.finishedAt(now)) {
            finish();
            return;
        }
        if (playedUpTo == NOTHING || playedUpTo > now) {
            playedUpTo = now;
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
        if (run == null || run.isPaused() || !wanted) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        List<TimerCues.Cue> cues = TimerCues.between(run, playedUpTo, now);
        playedUpTo = now;
        TimerView.dispatch(run.preset(), cues, this);
        for (TimerCues.Cue cue : cues) {
            // What was played with the screen off, for the device lane (verify-timer-locked.sh):
            // on some images the emulator's sound file cannot say when a sound came.
            android.util.Log.d("reteclock", "locked cue: " + cue.kind + " " + cue.interval);
        }
        if (run.finishedAt(now)) {
            finishing = true;
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
        refreshNotification(now);
        handler.postDelayed(stepper, delay);
    }

    /** After the finish: wait for its sound to end, then go. */
    private final Runnable finishWhenQuiet = new Runnable() {
        private long waited;

        @Override
        public void run() {
            waited += 1000L;
            if (player.isPlaying() && waited < FINISH_GRACE_MS) {
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
        release();
        player.fadeOutAndStop();
        refreshNotification(now);
    }

    private void resume() {
        if (run == null || !run.isPaused()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        run = run.resumedAt(now);
        playedUpTo = now;
        remember();
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
        finish();
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
        release();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
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
        if (message == null || message.isEmpty() || !CueSound.canSpeak(this)) {
            return;
        }
        if (voice == null) {
            voice = new TimerVoice(this);
        }
        voice.say(message, SystemClock.elapsedRealtime(),
                Settings.soundGain(this, com.reteclock.core.SoundLevels.TIMER_MESSAGES));
    }

    @Override
    public void flash() {
        // Nothing to flash with the screen off.
    }

    // ---- the notification -----------------------------------------------------------------------

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
        PendingIntent open = PendingIntent.getActivity(this, 20,
                new Intent(this, ClockActivity.class).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT);
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
