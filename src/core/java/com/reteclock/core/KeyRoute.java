package com.reteclock.core;

/**
 * What a hardware key does while the clock is on screen.
 *
 * The clock's face is deliberately bare — a desk clock is meant to be looked at, not operated — so
 * the ways in are gestures nothing on screen advertises: a tap for the menu, a long press for the
 * settings. A phone that still has a Menu key already carries the label this app cannot draw, and
 * pressing it is what somebody looking for the settings will try first (issue #40).
 *
 * Everything else on the key row belongs to the platform. Back leaves the clock, the rocker is the
 * volume, and a key this app has never heard of is not this app's business: a clock that swallowed
 * those would be a clock you could not put down. So the rule names the one key it takes rather than
 * a range, and answers {@link #NOTHING} for all the rest.
 *
 * Pure Java: the caller passes {@code android.view.KeyEvent} codes, and the rule is one testable
 * sentence rather than a condition buried in an activity.
 */
public final class KeyRoute {

    /** Not the app's key: let it reach the platform untouched. */
    public static final int NOTHING = 0;
    /** Open the general settings, as a long press on the clock does. */
    public static final int SETTINGS = 1;

    /** {@code android.view.KeyEvent.KEYCODE_MENU}, named here so the core imports no android.*. */
    private static final int KEYCODE_MENU = 82;

    private KeyRoute() {
    }

    /**
     * @param keyCode  the {@code KeyEvent} key code that was pressed
     * @return {@link #SETTINGS} or {@link #NOTHING}
     */
    public static int onClock(int keyCode) {
        return keyCode == KEYCODE_MENU ? SETTINGS : NOTHING;
    }
}
