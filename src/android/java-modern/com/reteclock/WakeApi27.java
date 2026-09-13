package com.reteclock;

import android.app.Activity;

/**
 * Showing the card over the lock screen and turning the screen on, the way Android 8.1 and up asks
 * for it (RFC-0012). Below 27 the window flags do the same job. Touched only behind
 * {@code SDK_INT >= 27}.
 */
final class WakeApi27 {

    private WakeApi27() {
    }

    static void showOverLockScreen(Activity activity) {
        activity.setShowWhenLocked(true);
        activity.setTurnScreenOn(true);
    }
}
