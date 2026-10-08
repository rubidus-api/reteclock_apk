package com.reteclock.core;

/**
 * When the web administrator listens (R140).
 *
 * <p>Until 0.55.1 there was one rule: while any of the app's screens is showing. That is still
 * what happens unless somebody chooses otherwise, and what an absent or unknown stored value
 * means. The narrower choice is the Web administration page alone: the server is then up only
 * while the person is looking at its address, and not all day behind a clock on the wall.
 *
 * <p>The widest (R141) is in the background too: a service of the app's own keeps the server up
 * when no screen of the app is showing, until it is stopped or the app is ended. With the screen
 * off it is kept awake only on the charger; on battery nothing is held and the device may sleep.
 *
 * <p>The value is a name and not a switch, so that another lifetime is another name.
 */
public final class WebLifetime {

    /** While the clock, the screensaver or any settings page is showing. */
    public static final String SCREEN = "screen";
    /** Only while the Web administration page is showing. */
    public static final String PAGE = "page";
    /** In the background too, behind the app's own service. */
    public static final String ALWAYS = "always";

    private WebLifetime() {
    }

    /** The stored value as one of the names above; anything else is the first rule. */
    public static String known(String stored) {
        return PAGE.equals(stored) ? PAGE : ALWAYS.equals(stored) ? ALWAYS : SCREEN;
    }

    /**
     * Whether the server should be listening just now.
     *
     * @param anyScreen whether any of the app's screens is showing
     * @param ownPage whether the Web administration page is among them
     * @param background whether the background service is up
     */
    public static boolean listens(String lifetime, boolean enabled, boolean anyScreen,
            boolean ownPage, boolean background) {
        if (!enabled) {
            return false;
        }
        String rule = known(lifetime);
        if (ALWAYS.equals(rule)) {
            return anyScreen || background;
        }
        return anyScreen && (!PAGE.equals(rule) || ownPage);
    }

    /** Whether the background service should be kept at all. */
    public static boolean background(String lifetime, boolean enabled) {
        return enabled && ALWAYS.equals(known(lifetime));
    }

    /** Whether the device is kept awake for the server with the screen off: only on the charger. */
    public static boolean staysAwake(String lifetime, boolean enabled, boolean plugged) {
        return plugged && background(lifetime, enabled);
    }
}
