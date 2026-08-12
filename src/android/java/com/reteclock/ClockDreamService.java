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
    private TimerSounds sounds;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        view = new ClockView(this);
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
        timer.adopt(running);

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
        return TimerMemory.restore(presets.get(Settings.timerChosen(this)),
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
        public void remember(TimerRun run) {
            if (run == null) {
                Settings.forgetRun(ClockDreamService.this);
            } else {
                Settings.rememberRun(ClockDreamService.this,
                        TimerMemory.originOf(run, android.os.SystemClock.elapsedRealtime()),
                        TimerMemory.pausedAtOf(run));
            }
        }

        @Override
        public void cue(Tones.Note[] pattern) {
            if (sounds == null) {
                sounds = new TimerSounds(ClockDreamService.this);
            }
            sounds.play(pattern, Settings.timerAlert(ClockDreamService.this));
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
    };

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        view.start();
        if (timer != null) {
            timer.resumeDrawing();
        }
    }

    @Override
    public void onDreamingStopped() {
        view.stop();
        if (timer != null) {
            timer.pauseDrawing();
        }
        super.onDreamingStopped();
    }
}
