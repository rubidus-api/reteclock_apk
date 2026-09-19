package com.reteclock.core;

/**
 * What the moments of the sun's day are called by the reckoning in force (issue #56).
 *
 * <p>The app's own names say what each moment is, and they never change. These are the names the
 * chosen set of numbers uses for the same moments, shown beside them when the user asks, so that a
 * page can be read against a printed timetable without translating in the head. They are labels,
 * not a claim: the app computes the sun, and what a moment is called is the tradition's business.
 */
public final class SunNames {

    private static final String[] ISLAMIC = {
        "", "Fajr", "Sunrise", "Dhuhr", "Asr", "Sunset", "Isha", "Midnight", "Maghrib"
    };

    private SunNames() {
    }

    /**
     * The name the set gives this event, or empty when the set gives it none — which is every
     * event when no set is chosen.
     *
     * The index is the event constant of {@link SunTimes}: 1 sunrise, 2 sunset, 3 noon, 4 dawn,
     * 5 dusk, 6 the night's middle, 7 the afternoon shadow, 8 the evening.
     */
    public static String of(int methodId, int event) {
        if (methodId == SunMethods.CUSTOM || !SunTimes.isEvent(event)) {
            return "";
        }
        switch (event) {
            case SunTimes.DAWN: return ISLAMIC[1];
            case SunTimes.SUNRISE: return ISLAMIC[2];
            case SunTimes.NOON: return ISLAMIC[3];
            case SunTimes.AFTERNOON_SHADOW: return ISLAMIC[4];
            case SunTimes.SUNSET: return ISLAMIC[5];
            case SunTimes.EVENING: return ISLAMIC[8];
            case SunTimes.DUSK: return ISLAMIC[6];
            case SunTimes.NIGHT_MIDDLE: return ISLAMIC[7];
            default: return "";
        }
    }
}
