package com.reteclock;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

import com.reteclock.core.TimerPreset;
import com.reteclock.core.Tones;

/**
 * Full-screen clock activity.
 *
 * Launched from the home screen, from a desk dock, or by {@link PowerConnectionReceiver} when the
 * device starts charging. It keeps the screen on for as long as it is visible.
 *
 * A long press opens the settings screen. Since nothing on screen says so, the clock says it once
 * on launch — but only when the user opened it themselves, and only until they have been there.
 */
public class ClockActivity extends Activity {

    /** Set by {@link PowerConnectionReceiver} so the clock knows it may show over the lock screen. */
    public static final String EXTRA_DOCK = "com.reteclock.DOCK";

    private ClockView view;
    /** The timer's strip beside the clock, or null when the timer is switched off. */
    private TimerView timer;
    /** Everything on screen: the clock, the strip, and the sheet the flash uses. */
    private FrameLayout root;
    private View flash;
    private TimerSounds sounds;
    private TimerVoice voice;
    /** This run leaves the imported images and fonts alone; the run before it never came back. */
    private boolean safeMode;
    private final android.os.Handler handler = new android.os.Handler();

    /**
     * Clears the mark this run left, once it has lasted long enough to be worth trusting.
     *
     * Late enough that the pictures have been decoded and drawn many times over: whatever is going
     * to hang the clock has had its chance by then, and a run that gets here really was healthy.
     */
    private final Runnable reportHealthy = new Runnable() {
        @Override
        public void run() {
            Settings.setRunUnfinished(ClockActivity.this, false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        // Asked to stay up: the same flags the charger already brings, but every time. The screen
        // never sleeps, the keyguard never covers the clock, and nothing takes it away — which is
        // what a clock on a wall, running a timer, is for.
        if (Settings.stayUnlocked(this)
                || (getIntent() != null && getIntent().getBooleanExtra(EXTRA_DOCK, false))) {
            flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        }
        getWindow().addFlags(flags);

        // Read the mark before this run writes its own: it belongs to the run before this one.
        safeMode = com.reteclock.core.SafeStart.safeMode(Settings.runUnfinished(this));
        if (safeMode) {
            Settings.setSafeNotice(this, true);
        }

        view = new ClockView(this, safeMode);
        // A tap opens the menu; a long press is the old way straight to the settings, kept because
        // people who have used the app know it.
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClockMenu.show(ClockActivity.this);
            }
        });
        view.setLongClickable(true);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(ClockActivity.this, SettingsActivity.class));
                return true;
            }
        });

        root = new FrameLayout(this);
        setContentView(root);
        layOutScreen();
        hideSystemBars();
        maybeHintAtSettings();
    }

    /**
     * Puts the clock on screen, with the timer's strip beside it when the timer is on: down the
     * left in landscape, across the top in portrait.
     *
     * Called again when the screen turns, because this activity handles its own configuration
     * changes and the strip has to change sides.
     */
    private void layOutScreen() {
        // The strip is built afresh each time; the one being dropped must stop ticking, or it goes
        // on sounding its own copy of the run in the background.
        if (timer != null) {
            timer.retire();
        }
        root.removeAllViews();
        if (view.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) view.getParent()).removeView(view);
        }

        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        boolean wantTimer = !safeMode && Settings.timerOn(this)
                && !Settings.timerPresets(this).isEmpty();

        if (!wantTimer) {
            timer = null;
            root.addView(view, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

            timer = new TimerView(this);
            timer.setListener(timerListener);
            List<TimerPreset> presets = Settings.timerPresets(this);
            TimerPreset chosen = presets.get(Settings.timerChosen(this));
            timer.setPreset(chosen);
            // A timer started before the screen was left, or turned, carries on from where it is.
            timer.adopt(com.reteclock.core.TimerMemory.restore(chosen,
                    Settings.runPreset(this), Settings.runOrigin(this),
                    Settings.runPausedAt(this), android.os.SystemClock.elapsedRealtime()));

            int strip = stripThickness(landscape);
            row.addView(timer, landscape
                    ? new LinearLayout.LayoutParams(strip, LinearLayout.LayoutParams.MATCH_PARENT)
                    : new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, strip));
            row.addView(view, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            root.addView(row, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        // The sheet the flash uses, above everything and invisible until it is wanted.
        flash = new View(this);
        flash.setBackgroundColor(Color.WHITE);
        flash.setVisibility(View.INVISIBLE);
        root.addView(flash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** The setting can be changed while the clock is up, so the flags follow it on every return. */
    private void applyStayUnlocked() {
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        if (Settings.stayUnlocked(this)) {
            getWindow().addFlags(flags);
        } else if (getIntent() == null || !getIntent().getBooleanExtra(EXTRA_DOCK, false)) {
            getWindow().clearFlags(flags);
        }
    }

    /** How wide the strip is: enough for the bar and its turned readouts, and no more. */
    private int stripThickness(boolean landscape) {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int shorter = Math.min(metrics.widthPixels, metrics.heightPixels);
        int wanted = Math.round(shorter * 0.16f);
        int floor = Math.round(56f * metrics.density);
        int ceiling = Math.round(120f * metrics.density);
        return Math.max(floor, Math.min(wanted, ceiling));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // The strip changes sides; the clock re-measures itself as it always did.
        layOutScreen();
        hideSystemBars();
    }

    /** What the strip asks the world for: sounds, speech, a flash, and the preset list. */
    private final TimerView.Listener timerListener = new TimerView.Listener() {
        @Override
        public void remember(com.reteclock.core.TimerRun run) {
            if (run == null) {
                Settings.forgetRun(ClockActivity.this);
            } else {
                Settings.rememberRun(ClockActivity.this,
                        com.reteclock.core.TimerMemory.identityOf(timer == null
                                ? null : timer.preset()),
                        com.reteclock.core.TimerMemory.originOf(run,
                                android.os.SystemClock.elapsedRealtime()),
                        com.reteclock.core.TimerMemory.pausedAtOf(run));
            }
        }

        @Override
        public void cue(Tones.Note[] pattern) {
            if (sounds == null) {
                sounds = new TimerSounds(ClockActivity.this);
            }
            sounds.play(pattern, Settings.timerAlert(ClockActivity.this));
        }

        @Override
        public void speak(String message) {
            if (message == null || message.isEmpty()
                    || Settings.timerAlert(ClockActivity.this) != Settings.ALERT_SOUND) {
                return;
            }
            if (voice == null) {
                voice = new TimerVoice(ClockActivity.this);
            }
            voice.say(message, android.os.SystemClock.elapsedRealtime());
        }

        @Override
        public void flash() {
            flashScreen(3);
        }

        @Override
        public void choosePreset() {
            showPresetList();
        }
    };

    /** Three quick blinks of the whole screen: an interval has ended. */
    private void flashScreen(final int times) {
        if (flash == null || times <= 0) {
            return;
        }
        flash.setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flash.setVisibility(View.INVISIBLE);
                if (times > 1) {
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            flashScreen(times - 1);
                        }
                    }, 90L);
                }
            }
        }, 70L);
    }

    /** The hourglass: which preset should the controls start? */
    private void showPresetList() {
        final List<TimerPreset> presets = Settings.timerPresets(this);
        if (presets.isEmpty() || timer == null) {
            return;
        }
        String[] names = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            names[i] = presets.get(i).name + "   "
                    + com.reteclock.core.TimeReadout.of(presets.get(i).totalMs());
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.timer_pick)
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Settings.setTimerChosen(ClockActivity.this, which);
                        timer.setPreset(presets.get(which));
                    }
                })
                .show();
    }

    /**
     * Says that a long press opens the settings, briefly.
     *
     * Not when the charger started the clock: that happens on a bedside stand, possibly in the
     * middle of the night, and a message nobody asked for has no business appearing there. And not
     * once the user has opened the settings, because then they know.
     */
    private void maybeHintAtSettings() {
        boolean startedByCharger =
                getIntent() != null && getIntent().getBooleanExtra(EXTRA_DOCK, false);
        // A safe run says so whatever started it: the user needs to know why their picture is gone
        // and where to go about it, and that outranks a quiet bedside.
        if (safeMode) {
            Toast.makeText(this, R.string.hint_safe_mode, Toast.LENGTH_LONG).show();
            return;
        }
        if (startedByCharger || Settings.hintSeen(this)) {
            return;
        }
        Toast.makeText(this, R.string.hint_touch, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have just changed the options in the settings screen — including whether
        // the timer is on at all, which changes what is on screen.
        view.reloadOptions();
        applyStayUnlocked();
        layOutScreen();
        view.start();
        if (timer != null) {
            timer.resumeDrawing();
        }
        // The mark this run leaves if it never comes back.
        Settings.setRunUnfinished(this, true);
        handler.removeCallbacks(reportHealthy);
        handler.postDelayed(reportHealthy, com.reteclock.core.SafeStart.HEALTHY_MS);
    }

    @Override
    protected void onPause() {
        view.stop();
        if (timer != null) {
            timer.pauseDrawing();
        }
        if (voice != null) {
            voice.release();
            voice = null;
        }
        // Being put aside is proof the clock was answering, so the mark comes off here too — the
        // long press into the settings is exactly the case a hung clock never reaches.
        handler.removeCallbacks(reportHealthy);
        Settings.setRunUnfinished(this, false);
        super.onPause();
    }


    /**
     * Hides the status and navigation bars where the platform supports it.
     *
     * The flag constants are compile-time integers, so referring to them costs nothing on
     * platforms that do not know them; the runtime check keeps the call itself safe.
     */
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 19) {
            view.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else if (Build.VERSION.SDK_INT >= 14) {
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }
}
