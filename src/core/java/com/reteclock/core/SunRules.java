package com.reteclock.core;

/**
 * The conventions the sun's day is read by (issue #56).
 *
 * <p>Three of the seven events are not settled by astronomy alone. Dawn and dusk are the sun at
 * some depth below the horizon, and the recognised authorities publish different depths; the
 * afternoon is a shadow of some length, and two lengths are in use; and where the night is divided,
 * it can be divided to the next sunrise or to the next dawn. One authority — Umm al-Qura — does not
 * use a depth for dusk at all but a fixed interval after the evening event.
 *
 * <p>This holds those numbers and nothing else. Angles are in tenths of a degree, so 18.5° is 185
 * and a preference file never has to carry a decimal point. Everything here is arithmetic; which
 * set of numbers to use is the user's, and {@link SunMethods} is the list of published ones.
 */
public final class SunRules {

    /** Tenths of a degree the sun is below the horizon at dawn. */
    public final int dawnTenths;
    /** Tenths of a degree at dusk, or 0 when {@link #duskMinutesAfterEvening} is used instead. */
    public final int duskTenths;
    /** Minutes after the evening event, for the authority that fixes dusk that way; 0 otherwise. */
    public final int duskMinutesAfterEvening;
    /**
     * Tenths of a degree for the evening event, or 0 when it is sunset itself.
     *
     * Most reckonings take it at sunset; some take it when the sun is a few degrees down.
     */
    public final int eveningTenths;
    /** How many times its own height a shadow has grown by at the afternoon event: 1 or 2. */
    public final int shadowMultiple;
    /** Whether the night is divided to the next dawn rather than to the next sunrise. */
    public final boolean nightEndsAtDawn;
    /** What to do where the sun never reaches the angle: one of {@code SunTimes.HIGH_*}. */
    public final int highRule;

    public SunRules(int dawnTenths, int duskTenths, int duskMinutesAfterEvening, int eveningTenths,
            int shadowMultiple, boolean nightEndsAtDawn, int highRule) {
        this.dawnTenths = angle(dawnTenths, 180);
        this.duskTenths = angle(duskTenths, 0);
        this.duskMinutesAfterEvening = duskMinutesAfterEvening < 0 ? 0
                : Math.min(duskMinutesAfterEvening, 240);
        this.eveningTenths = angle(eveningTenths, 0);
        this.shadowMultiple = SunTimes.shadowChoice(shadowMultiple);
        this.nightEndsAtDawn = nightEndsAtDawn;
        this.highRule = SunTimes.highChoice(highRule);
    }

    /** An angle in tenths, kept to something a sun can actually be at; 0 means "not by angle". */
    private static int angle(int tenths, int fallback) {
        if (tenths <= 0) {
            return tenths == 0 ? 0 : fallback;
        }
        return Math.min(tenths, 300);
    }

    /** The plain-degrees shape the app used before the published sets existed. */
    public static SunRules of(int twilightDegrees, int shadowMultiple, int highRule) {
        int tenths = SunTimes.twilightChoice(twilightDegrees) * 10;
        return new SunRules(tenths, tenths, 0, 0, shadowMultiple, false, highRule);
    }

    /** The same rules with a different answer for the far north. */
    public SunRules withHighRule(int rule) {
        return new SunRules(dawnTenths, duskTenths, duskMinutesAfterEvening, eveningTenths,
                shadowMultiple, nightEndsAtDawn, rule);
    }

    /** The same rules with a different shadow. */
    public SunRules withShadow(int multiple) {
        return new SunRules(dawnTenths, duskTenths, duskMinutesAfterEvening, eveningTenths,
                multiple, nightEndsAtDawn, highRule);
    }

    /** Whether dusk is a fixed interval rather than an angle. */
    public boolean duskByInterval() {
        return duskMinutesAfterEvening > 0 && duskTenths == 0;
    }

    /** Whether the evening event is sunset itself. */
    public boolean eveningIsSunset() {
        return eveningTenths == 0;
    }

    /** An angle written the way a page shows it: 18.5 rather than 185. */
    public static String degrees(int tenths) {
        if (tenths % 10 == 0) {
            return Integer.toString(tenths / 10);
        }
        return (tenths / 10) + "." + (tenths % 10);
    }
}
