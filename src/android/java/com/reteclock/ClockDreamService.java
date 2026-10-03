package com.reteclock;

import android.content.res.Configuration;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

import com.reteclock.core.TimerMemory;
import com.reteclock.core.TimerPreset;
import com.reteclock.core.TimerRun;
import com.reteclock.core.Tones;

/**
 * The clock as a system screensaver (Daydream), available on Android 4.2 and newer.
 *
 * The class extends an API 17 type. Older platforms never load it because they do not have a
 * Daydream host, so declaring the service in the manifest stays safe down to the minimum SDK.
 *
 * A timer started on the clock keeps running here: the strip appears beside the clock, shows where
 * the run has got to, and sounds its cues. It is **shown, not driven** — a screensaver is dismissed
 * by touching it, so a control the user could press is a control they can never press. Starting and
 * stopping stay on the clock, where a touch means what it says.
 *
 * A clock meant to stay up rather than hand over to a screensaver is a different thing: that is the
 * "keep the clock up, past the lock screen" setting, which keeps the screen on so no screensaver
 * ever begins.
 */
public class ClockDreamService extends DreamService {

    private ClockView view;
    private TimerView timer;
    /** Whether the clock is asleep by the schedule (issue #54). */
    private SleepWatch sleepWatch;
    /** The screensaver sounds a cue the same way the clock does; it simply never speaks. */
    private final SoundPlayer cuePlayer = new SoundPlayer();
    private BellRinger bells;
    private SlideWatch slideWatch;
    private TimerSounds sounds;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        view = new ClockView(this);
        // The screensaver is the clock, so the bells ring here too. Touching a screensaver
        // dismisses it, which stops the sound with it — there is nothing else for a touch to do.
        final BellRinger bells = new BellRinger(this);
        slideWatch = new SlideWatch(this);
        sleepWatch = new SleepWatch(this);
        this.bells = bells;
        bells.setBusy(new BellRinger.Busy() {
            @Override
            public boolean timerIsRunning() {
                return timer != null && timer.isRunning();
            }
        });
        view.setOnSecond(new ClockView.OnSecond() {
            @Override
            public void second(long nowMs) {
                bells.tick(nowMs);
                sayTheHour(nowMs);
                if (slideWatch.changed(nowMs)) {
                    view.reloadOptions();
                }
                // The screensaver has no sleep button, but it follows the schedule: the background
                // and the brightness (issue #54).
                if (sleepWatch.changed(nowMs)) {
                    view.reloadOptions();
                    applySleepBrightness();
                }
            }
        });
        TimerRun running = restoreRun();
        if (running == null) {
            setContentView(view);
            return;
        }

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        timer = new TimerView(this);
        timer.setListener(dreamListener);
        timer.setPreset(running.preset());
        // Back from a dark screen, the run is the one the service left written down and the cues
        // carry on after the last one it played (R130).
        long handed = TimerSoundService.takeBack(this);
        if (handed != TimerSoundService.NOTHING) {
            timer.handOver(running, Settings.runStarted(this), handed);
        } else {
            timer.adopt(running, Settings.runStarted(this));
        }
        // Under OLED care the controls take the mode's dim colour too, as they do on the clock.
        if (Settings.oledCare(this)) {
            timer.setChrome(com.reteclock.core.OledCare.TEXT_COLOR);
        }

        int strip = stripThickness();
        row.addView(timer, landscape
                ? new LinearLayout.LayoutParams(strip, LinearLayout.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, strip));
        row.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        setContentView(row);
    }

    /** The run the clock left behind, if the timer is on and there is one worth showing. */
    private TimerRun restoreRun() {
        if (!Settings.timerOn(this)) {
            return null;
        }
        List<TimerPreset> presets = Settings.timerPresets(this);
        if (presets.isEmpty()) {
            return null;
        }
        return TimerMemory.restore(presets.get(Settings.timerChosen(this)), Settings.runPreset(this),
                Settings.runOrigin(this), Settings.runPausedAt(this),
                android.os.SystemClock.elapsedRealtime());
    }

    private int stripThickness() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int shorter = Math.min(metrics.widthPixels, metrics.heightPixels);
        int wanted = Math.round(shorter * 0.16f);
        int floor = Math.round(56f * metrics.density);
        int ceiling = Math.round(120f * metrics.density);
        return Math.max(floor, Math.min(wanted, ceiling));
    }

    /**
     * The screensaver plays what the timer reaches and says nothing about what it does not do: the
     * flash is left out, because a screensaver whitening the whole screen at three in the morning
     * is not a warning, it is a fright — and the clock, where the timer was started, does it there.
     */
    private final TimerView.Listener dreamListener = new TimerView.Listener() {
        @Override
        public void remember(TimerRun run, long startEpochMs) {
            if (run == null) {
                Settings.forgetRun(ClockDreamService.this);
            } else {
                Settings.rememberRun(ClockDreamService.this,
                        TimerMemory.identityOf(timer == null ? null : timer.preset()),
                        TimerMemory.originOf(run, android.os.SystemClock.elapsedRealtime()),
                        TimerMemory.pausedAtOf(run), startEpochMs);
            }
        }

        @Override
        public void runEnded(TimerRun run, long startEpochMs, long elapsedMs) {
            ClockActivity.logRun(ClockDreamService.this, run, startEpochMs, elapsedMs);
        }

        /**
         * The screensaver has no way to open a settings screen — a dream that launched an activity
         * would be a dream that ended itself — so the L is not put on its strip in the first place.
         */
        @Override
        public void openTimerSettings() {
        }

        @Override
        public void cue(Tones.Note[] pattern) {
            if (sounds == null) {
                sounds = new TimerSounds(ClockDreamService.this);
            }
            CueSound.cue(ClockDreamService.this, sounds, pattern);
        }

        @Override
        public void sound(String name, Tones.Note[] fallback) {
            if (sounds == null) {
                sounds = new TimerSounds(ClockDreamService.this);
            }
            CueSound.play(ClockDreamService.this, cuePlayer, sounds, name, fallback);
        }

        @Override
        public void speak(String message) {
            // Left to the clock: a screensaver that starts talking is harder to explain than one
            // that beeps, and the engine would have to be held open for the whole night.
        }

        @Override
        public void flash() {
        }

        @Override
        public void choosePreset() {
        }

        @Override
        public void toggleSleep() {
        }
    };

    /** The window's brightness: the sleep setting while asleep, otherwise the phone's own. */
    private void applySleepBrightness() {
        if (getWindow() == null || sleepWatch == null) {
            return;
        }
        android.view.WindowManager.LayoutParams params = getWindow().getAttributes();
        int sleeping = Settings.sleepBrightness(this);
        params.screenBrightness = sleepWatch.asleep()
                ? com.reteclock.core.SleepMode.windowBrightness(sleeping)
                : com.reteclock.core.ScreenDim.FOLLOW_SYSTEM;
        getWindow().setAttributes(params);
    }

    /** When {@link #sayTheHour} last looked; reset as the screensaver starts. */
    private long hourLooked = com.reteclock.core.HourChime.NEVER;
    /** The voice for the hour, made only if it is ever needed and let go when the dream stops. */
    private TimerVoice hourVoice;

    /**
     * The time said as each hour turns, as on the clock (issue #67): not asleep, not while a timer
     * is counting or a bell ringing, at the Volume card's level for the spoken time.
     */
    private void sayTheHour(long nowMs) {
        long last = hourLooked;
        hourLooked = nowMs;
        if (!Settings.speakHour(this) || !com.reteclock.core.HourChime.due(last, nowMs,
                Settings.offsetMinutes(this, nowMs))) {
            return;
        }
        if ((sleepWatch != null && sleepWatch.asleep()) || (timer != null && timer.isRunning())
                || (bells != null && (bells.isRinging() || bells.justRang()))) {
            return;
        }
        float gain = Settings.soundGain(this, com.reteclock.core.SoundLevels.SPOKEN_TIME);
        if (gain <= 0f) {
            return;
        }
        if (hourVoice == null) {
            hourVoice = new TimerVoice(this);
        }
        hourVoice.say(Settings.spokenTimeNow(this), android.os.SystemClock.elapsedRealtime(), gain);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        hourLooked = com.reteclock.core.HourChime.NEVER;
        if (bells != null) {
            bells.reload();
        }
        if (slideWatch != null) {
            slideWatch.reload();
        }
        if (sleepWatch != null) {
            sleepWatch.reload();
            applySleepBrightness();
        }
        view.start();
        if (timer != null) {
            timer.resumeDrawing();
        }
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        if (hourVoice != null) {
            hourVoice.release();
            hourVoice = null;
        }
        cuePlayer.stopNow();
        if (bells != null) {
            bells.stop();
        }
        if (timer != null) {
            timer.pauseDrawing();
            // The screensaver ends when the screen goes off; a run going carries on sounding.
            if (timer.isRunning() && Settings.timerWhileLocked(this)
                    && !ClockActivity.screenOn(this)) {
                TimerSoundService.startFor(this, timer.lastCueMs());
            }
        }
        super.onDreamingStopped();
    }
}
