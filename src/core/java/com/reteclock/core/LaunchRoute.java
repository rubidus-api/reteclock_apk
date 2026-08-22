package com.reteclock.core;

/**
 * Where a tap on the home-screen button goes: the clock, or the settings.
 *
 * The app began by opening the clock. Then an imported picture could cost more than the device had,
 * and a full-screen clock that has stopped answering has no controls on its face and will not take
 * a long press — so the button was pointed at the settings instead, which is a door that always
 * opens (R29). It cost every ordinary launch a press, which is what issue #39 asked about.
 *
 * The door is no longer the only protection: a run writes a mark and clears it once it has been
 * drawing happily, so a start that finds the mark knows the run before it never came back
 * ({@link SafeStart}). That is the case the door was built for, and it is now detected rather than
 * guarded against. So the button opens the clock, and the settings are reached from the clock, from
 * the icon's own long-press menu where the platform offers one — and automatically, whenever the
 * previous run did not come back.
 *
 * Pure Java, so the rule is one testable sentence rather than three conditions spread over two
 * activities.
 */
public final class LaunchRoute {

    /** Straight to the clock. */
    public static final int CLOCK = 0;
    /** To the settings, where the clock is one press away. */
    public static final int SETTINGS = 1;

    private LaunchRoute() {
    }

    /**
     * @param fromHomeScreen  whether this start carries the launcher category; a dock, a charger or
     *                        the screensaver is not a person looking for a way in, and goes to the
     *                        clock whatever else is true
     * @param directStart     the user's own answer, on by default
     * @param previousRunUnfinished  the mark: the run before this one never reported itself healthy
     */
    public static int of(boolean fromHomeScreen, boolean directStart,
            boolean previousRunUnfinished) {
        if (!fromHomeScreen) {
            return CLOCK;
        }
        // A run that did not come back overrules the preference in either direction: it is exactly
        // the case the settings door exists for, and the person holding the phone is more likely to
        // be looking for the way out of it than for the time.
        if (previousRunUnfinished) {
            return SETTINGS;
        }
        return directStart ? CLOCK : SETTINGS;
    }
}
