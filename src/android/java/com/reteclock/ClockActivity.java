package com.reteclock;

import android.app.Activity;
import android.content.SharedPreferences;
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
 * A long press toggles the "start when charging" setting, which is the only setting the app has.
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
                toggleStartWhenCharging();
                return true;
            }
        });
        setContentView(view);
        hideSystemBars();
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
        view.start();
    }

    @Override
    protected void onPause() {
        view.stop();
        super.onPause();
    }

    private void toggleStartWhenCharging() {
        SharedPreferences prefs = Settings.prefs(this);
        boolean enabled = !Settings.startWhenCharging(this);
        prefs.edit().putBoolean(Settings.KEY_START_WHEN_CHARGING, enabled).commit();
        Toast.makeText(this,
                enabled ? R.string.charging_start_on : R.string.charging_start_off,
                Toast.LENGTH_SHORT).show();
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
