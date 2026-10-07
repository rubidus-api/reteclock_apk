package com.reteclock.core;

/**
 * When the web administrator listens (R140).
 *
 * <p>Until 0.55.1 there was one rule: while any of the app's screens is showing. That is still
 * what happens unless somebody chooses otherwise, and what an absent or unknown stored value
 * means. The narrower choice is the Web administration page alone: the server is then up only
 * while the person is looking at its address, and not all day behind a clock on the wall.
 *
 * <p>The value is a name and not a switch, so that another lifetime is another name.
 */
public final class WebLifetime {

    /** While the clock, the screensaver or any settings page is showing. */
    public static final String SCREEN = "screen";
    /** Only while the Web administration page is showing. */
    public static final String PAGE = "page";

    private WebLifetime() {
    }

    /** The stored value as one of the names above; anything else is the first rule. */
    public static String known(String stored) {
        return PAGE.equals(stored) ? PAGE : SCREEN;
    }

    /**
     * Whether the server should be listening just now.
     *
     * @param anyScreen whether any of the app's screens is showing
     * @param ownPage whether the Web administration page is among them
     */
    public static boolean listens(String lifetime, boolean enabled, boolean anyScreen,
            boolean ownPage) {
        if (!enabled || !anyScreen) {
            return false;
        }
        return !PAGE.equals(known(lifetime)) || ownPage;
    }
}
