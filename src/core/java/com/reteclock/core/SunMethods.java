package com.reteclock.core;

/**
 * The published sets of numbers the sun's day is commonly read by (issue #56, the owner's request
 * for presets).
 *
 * <p>Choosing "Muslim World League" is something a person can do; choosing "eighteen degrees" is
 * expert knowledge. So the page offers the sets by the name of the body that published them, and
 * shows the numbers each one sets, which is the whole of what a set is. The app is not ruling on
 * anything by carrying them — it is saving the user from typing numbers they would otherwise have
 * to look up — and every one of them can be changed afterwards, which turns the choice into
 * {@link #CUSTOM}.
 *
 * <p>The values are the ones these bodies publish for the angles at which dawn and dusk are taken,
 * and are the same numbers every prayer-time calculator carries. They are worth checking against a
 * local timetable, and the page says so.
 */
public final class SunMethods {

    /** One published set: its name, and the numbers it sets. */
    public static final class Method {
        public final int id;
        public final String name;
        public final SunRules rules;

        Method(int id, String name, SunRules rules) {
            this.id = id;
            this.name = name;
            this.rules = rules;
        }
    }

    public static final int CUSTOM = 0;

    /** The sets, in the order the page lists them. {@code CUSTOM} is first: it is the user's own. */
    private static final Method[] ALL = {
        new Method(CUSTOM, "Custom", SunRules.of(SunTimes.DEFAULT_TWILIGHT_DEGREES, 1,
                SunTimes.DEFAULT_HIGH_RULE)),
        new Method(1, "Muslim World League",
                new SunRules(180, 170, 0, 0, 1, false, SunTimes.HIGH_ANGLE_SHARE)),
        new Method(2, "Islamic Society of North America",
                new SunRules(150, 150, 0, 0, 1, false, SunTimes.HIGH_ANGLE_SHARE)),
        new Method(3, "Egyptian General Authority of Survey",
                new SunRules(195, 175, 0, 0, 1, false, SunTimes.HIGH_ANGLE_SHARE)),
        new Method(4, "Umm al-Qura, Makkah",
                new SunRules(185, 0, 90, 0, 1, false, SunTimes.HIGH_ANGLE_SHARE)),
        new Method(5, "University of Islamic Sciences, Karachi",
                new SunRules(180, 180, 0, 0, 1, false, SunTimes.HIGH_ANGLE_SHARE)),
        new Method(6, "Institute of Geophysics, University of Tehran",
                new SunRules(177, 140, 0, 45, 1, true, SunTimes.HIGH_ANGLE_SHARE)),
        new Method(7, "Shia Ithna-Ashari (Jafari)",
                new SunRules(160, 140, 0, 40, 1, true, SunTimes.HIGH_ANGLE_SHARE)),
    };

    private SunMethods() {
    }

    public static Method[] all() {
        return ALL.clone();
    }

    /** The set with this id, or {@link #CUSTOM}'s. */
    public static Method of(int id) {
        for (Method method : ALL) {
            if (method.id == id) {
                return method;
            }
        }
        return ALL[0];
    }

    /**
     * Which set these rules are, or {@link #CUSTOM} when they are nobody's published set — which is
     * what a rule changed by hand becomes.
     */
    public static int idOf(SunRules rules) {
        for (Method method : ALL) {
            if (method.id == CUSTOM) {
                continue;
            }
            SunRules other = method.rules;
            if (rules.dawnTenths == other.dawnTenths && rules.duskTenths == other.duskTenths
                    && rules.duskMinutesAfterEvening == other.duskMinutesAfterEvening
                    && rules.eveningTenths == other.eveningTenths
                    && rules.nightEndsAtDawn == other.nightEndsAtDawn) {
                return method.id;
            }
        }
        return CUSTOM;
    }
}
