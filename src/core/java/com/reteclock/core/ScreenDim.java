package com.reteclock.core;

/**
 * How dark a tap makes the clock, and how it is given back (issue #41).
 *
 * A clock left running by the bed is too bright at night, and the screen it is drawn on is the only
 * control a bare clock face has. So a tap dims the screen right down and the next tap hands it
 * back — the behaviour of the phone's own bedside clocks, and the reason the tap is no longer spent
 * opening a menu (R86).
 *
 * Giving it back is handing the screen to the platform rather than setting a level. The request
 * says "its previous level", and that level is whatever the phone is set to — which may be
 * automatic brightness following the room, something no number this app remembers could reproduce.
 * {@link #FOLLOW_SYSTEM} is the window saying it has no opinion, which stays right however the
 * light in the room changes while the clock is dark.
 *
 * The values are {@code android.view.WindowManager.LayoutParams.screenBrightness}: 0..1, or
 * {@code BRIGHTNESS_OVERRIDE_NONE} (-1) for no opinion. Pure Java, so the rule is testable without
 * a device.
 */
public final class ScreenDim {

    /** {@code BRIGHTNESS_OVERRIDE_NONE}: the screen goes back to the phone's own setting. */
    public static final float FOLLOW_SYSTEM = -1f;

    /**
     * As dark as the clock will go.
     *
     * Not zero. Zero is the darkest the platform allows and on some panels that is a screen showing
     * nothing at all — and a clock you cannot see is a clock you cannot find to tap, with the way
     * out of the dark drawn in the dark. This is dark enough to sleep beside and still lit enough
     * to read the hour and to aim a finger at.
     */
    public static final float DIM = 0.01f;

    private ScreenDim() {
    }

    /** The state after a tap. */
    public static boolean next(boolean dimmed) {
        return !dimmed;
    }

    /** What the window's {@code screenBrightness} should be in that state. */
    public static float brightness(boolean dimmed) {
        return dimmed ? DIM : FOLLOW_SYSTEM;
    }
}
