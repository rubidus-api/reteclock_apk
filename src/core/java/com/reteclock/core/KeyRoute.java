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
    /** Start the timer, or start it again from where it was paused. */
    public static final int TIMER_START = 2;
    /** Pause the timer where it stands. */
    public static final int TIMER_PAUSE = 3;
    /** Open the menu, as a long press on the clock face does. */
    public static final int MENU = 4;
    /** Dim the screen, or hand it back — what a tap does (issue #41). */
    public static final int DIM = 5;
    public static final int UNDIM = 6;

    /** {@code android.view.KeyEvent} codes, named here so the core imports no android.*. */
    private static final int KEYCODE_MENU = 82;
    private static final int KEYCODE_DPAD_UP = 19;
    private static final int KEYCODE_DPAD_DOWN = 20;
    private static final int KEYCODE_DPAD_CENTER = 23;
    private static final int KEYCODE_ENTER = 66;
    private static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    private static final int KEYCODE_MEDIA_PLAY = 126;
    private static final int KEYCODE_MEDIA_PAUSE = 127;
    private static final int KEYCODE_MEDIA_STOP = 86;
    /** Stop the timer and forget the run, which is what the strip's square does. */
    public static final int TIMER_STOP = 7;

    private KeyRoute() {
    }

    /**
     * @param keyCode  the {@code KeyEvent} key code that was pressed
     * @return {@link #SETTINGS} or {@link #NOTHING}
     */
    public static int onClock(int keyCode) {
        return keyCode == KEYCODE_MENU ? SETTINGS : NOTHING;
    }

    /**
     * What a key does on the clock face when there is no finger to do it with (issue #45).
     *
     * A television is driven by five keys and Enter, and the clock's own ways in are a tap and a
     * long press — gestures a remote does not have. So the same three things the face already does
     * are given keys, and nothing else is taken.
     *
     * **Centre is the timer.** On a clock face the timer is the only thing there is to operate, and
     * one key that starts what is stopped and stops what is running is the whole of a transport —
     * which is exactly what the reporter asked for. Held down, centre is the long press: the menu.
     * A remote that has a Menu key of its own opens the settings with it, as a phone does.
     *
     * Up and down dim the screen and hand it back, which is what a tap does. That is a keyboard and
     * a dock affordance rather than a television one: a set top box cannot dim a television.
     *
     * Everything else answers {@link #NOTHING}, Back most of all. A clock that swallowed Back on a
     * television would be a clock nobody could leave.
     *
     * @param keyCode      the {@code KeyEvent} key code
     * @param longPress    whether the platform says this is the held-down repeat
     * @param timerRunning whether something is counting just now
     */
    public static int onClock(int keyCode, boolean longPress, boolean timerRunning) {
        if (keyCode == KEYCODE_MENU) {
            return SETTINGS;
        }
        if (keyCode == KEYCODE_DPAD_CENTER || keyCode == KEYCODE_ENTER) {
            if (longPress) {
                return MENU;
            }
            return timerRunning ? TIMER_PAUSE : TIMER_START;
        }
        if (keyCode == KEYCODE_MEDIA_PLAY_PAUSE) {
            return timerRunning ? TIMER_PAUSE : TIMER_START;
        }
        if (keyCode == KEYCODE_MEDIA_PLAY) {
            return TIMER_START;
        }
        if (keyCode == KEYCODE_MEDIA_PAUSE) {
            return TIMER_PAUSE;
        }
        if (keyCode == KEYCODE_MEDIA_STOP) {
            return TIMER_STOP;
        }
        if (keyCode == KEYCODE_DPAD_DOWN) {
            return DIM;
        }
        if (keyCode == KEYCODE_DPAD_UP) {
            return UNDIM;
        }
        return NOTHING;
    }
}
