package com.reteclock;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_DOCK, false)) {
            flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        }
        getWindow().addFlags(flags);

        view = new ClockView(this);
        view.setLongClickable(true);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(ClockActivity.this, SettingsActivity.class));
                return true;
            }
        });
        setContentView(view);
        hideSystemBars();
        maybeHintAtSettings();
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
        if (startedByCharger || Settings.hintSeen(this)) {
            return;
        }
        Toast.makeText(this, R.string.hint_long_press, Toast.LENGTH_LONG).show();
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
        // The user may have just changed the options in the settings screen.
        view.reloadOptions();
        view.start();
    }

    @Override
    protected void onPause() {
        view.stop();
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
