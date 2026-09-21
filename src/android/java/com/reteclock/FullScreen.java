package com.reteclock;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * The size of the screen the clock is drawn on, as the clock gets it (issue #60).
 *
 * Not {@code getResources().getDisplayMetrics()}. On a phone with a navigation bar — buttons, or
 * the thin handle of gesture navigation — that answers the screen *less the bar*, because it is
 * what an ordinary window gets. The clock is not an ordinary window: from Android 4.4 it hides the
 * bars and lays itself out under them, so it is taller (or, lying down, wider) by exactly the bar.
 * A layout placed against the smaller number sits short of the edge by that much, and the editor
 * showed a screen of a different shape from the one the clock was about to fill.
 *
 * Before 4.4 the bars cannot be hidden for good, the clock gets what an ordinary window gets, and
 * so does this.
 */
final class FullScreen {

    private FullScreen() {
    }

    /** Width and height in pixels, the way the device is turned now. */
    static int[] size(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        // 19, not 17 where getRealMetrics arrived: the real size is only the clock's size when the
        // bars can be hidden, and that is 4.4's immersive mode.
        if (Build.VERSION.SDK_INT >= 19) {
            try {
                WindowManager windows =
                        (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                DisplayMetrics real = new DisplayMetrics();
                windows.getDefaultDisplay().getRealMetrics(real);
                if (real.widthPixels > 0 && real.heightPixels > 0) {
                    width = real.widthPixels;
                    height = real.heightPixels;
                }
            } catch (RuntimeException unavailable) {
                // The window's own size is a smaller clock, never a broken one.
            }
        }
        return new int[] {Math.max(1, width), Math.max(1, height)};
    }

    /** The shorter edge of {@link #size}. */
    static int shortEdge(Context context) {
        int[] size = size(context);
        return Math.min(size[0], size[1]);
    }

    /** The longer edge of {@link #size}. */
    static int longEdge(Context context) {
        int[] size = size(context);
        return Math.max(size[0], size[1]);
    }
}
