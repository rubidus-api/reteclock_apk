package com.reteclock.core;

/**
 * When the sun rises and sets at a place on a day (issue #55, RFC-0015).
 *
 * <p>The NOAA solar-calculator approximation: the fractional year gives the equation of time and the
 * sun's declination, and the hour angle at which the sun's centre is 0.833° below the horizon —
 * half its disc and the usual allowance for refraction — gives the event. Good to a minute or two
 * away from the poles, which is well inside what a bell needs; no network and no table of years.
 *
 * <p>Where the sun does not cross that line that day — a polar summer or winter — there is no event,
 * and {@link #NONE} says so rather than a made-up minute.
 *
 * <p>Pure Java, so it is tested without a device.
 */
public final class SunTimes {

    /** No sunrise, or no sunset, that day. */
    public static final int NONE = Integer.MIN_VALUE;

    public static final int SUNRISE = 1;
    public static final int SUNSET = 2;
    /** The sun at its highest: local solar noon (issue #56). */
    public static final int NOON = 3;
    /** Dawn: the sun rising through the chosen angle below the horizon. */
    public static final int DAWN = 4;
    /** Dusk: the sun sinking through it again. */
    public static final int DUSK = 5;
    /** Halfway between sunset and the next sunrise. */
    public static final int NIGHT_MIDDLE = 6;
    /**
     * The afternoon hour when a standing thing's shadow has grown by the chosen multiple of its own
     * height beyond the shadow it cast at noon.
     */
    public static final int AFTERNOON_SHADOW = 7;
    /**
     * The evening event: sunset itself in most reckonings, and the sun a few degrees down in some
     * (issue #56). Dusk can be counted from it as a fixed interval rather than an angle.
     */
    public static final int EVENING = 8;

    /** How far below the horizon dawn and dusk are taken to be, unless the user says otherwise. */
    public static final int DEFAULT_TWILIGHT_DEGREES = 18;
    /** The angles the page offers: civil, nautical, and the ones twilight tables commonly use. */
    public static final int[] TWILIGHT_CHOICES = {6, 12, 15, 17, 18, 19};

    /**
     * What to do on a day when the sun never reaches the angle dawn and dusk are read at — the far
     * north in summer, the far south in winter (issue #56, the owner's question about white nights).
     *
     * <p>There is no astronomical answer: the event does not happen. What exists are conventions,
     * and they disagree, so the app offers them by name and chooses none of them by itself.
     */
    public static final int HIGH_NOTHING = 0;
    /** Halfway through the night: dawn is sunrise minus half the night, dusk sunset plus half. */
    public static final int HIGH_MIDDLE_OF_NIGHT = 1;
    /** A seventh of the night before sunrise, and a seventh after sunset. */
    public static final int HIGH_SEVENTH_OF_NIGHT = 2;
    /** The angle as a share of the night: one sixtieth of it per degree. */
    public static final int HIGH_ANGLE_SHARE = 3;
    /** The time it had on the nearest earlier day when the sun did reach the angle. */
    public static final int HIGH_NEAREST_DAY = 4;
    public static final int[] HIGH_CHOICES = {HIGH_NOTHING, HIGH_MIDDLE_OF_NIGHT,
        HIGH_SEVENTH_OF_NIGHT, HIGH_ANGLE_SHARE, HIGH_NEAREST_DAY};
    public static final int DEFAULT_HIGH_RULE = HIGH_NOTHING;

    /** How far back the nearest-day rule will look before giving up. */
    private static final int NEAREST_DAY_LIMIT = 182;

    /** A rule made safe: one of {@link #HIGH_CHOICES}, or the default. */
    public static int highChoice(int rule) {
        for (int choice : HIGH_CHOICES) {
            if (choice == rule) {
                return rule;
            }
        }
        return DEFAULT_HIGH_RULE;
    }

    /** How many times its own height the shadow grows by, unless the user says otherwise. */
    public static final int DEFAULT_SHADOW_MULTIPLE = 1;
    public static final int[] SHADOW_CHOICES = {1, 2};

    /** The sun's centre this far below the horizon is sunrise or sunset: half its disc, plus air. */
    private static final double HORIZON_ZENITH = 90.833;

    private SunTimes() {
    }

    /** Whether this is an event this version knows. */
    public static boolean isEvent(int event) {
        return event >= SUNRISE && event <= EVENING;
    }

    /** An angle made safe: one of {@link #TWILIGHT_CHOICES}, or the default. */
    public static int twilightChoice(int degrees) {
        for (int choice : TWILIGHT_CHOICES) {
            if (choice == degrees) {
                return degrees;
            }
        }
        return DEFAULT_TWILIGHT_DEGREES;
    }

    /** A shadow multiple made safe: one of {@link #SHADOW_CHOICES}, or the default. */
    public static int shadowChoice(int multiple) {
        for (int choice : SHADOW_CHOICES) {
            if (choice == multiple) {
                return multiple;
            }
        }
        return DEFAULT_SHADOW_MULTIPLE;
    }

    /**
     * The event, in minutes from UTC midnight of the civil day {@code jdn}, or {@link #NONE}.
     *
     * The answer can lie outside 0..1440 for a place far from Greenwich; the caller adds its offset
     * and folds. {@code event} is {@link #SUNRISE} or {@link #SUNSET}.
     */
    public static int utcMinute(int jdn, double latitude, double longitude, int event) {
        return utcMinute(jdn, latitude, longitude, event, DEFAULT_TWILIGHT_DEGREES,
                DEFAULT_SHADOW_MULTIPLE);
    }

    /**
     * The same, for the events that need a convention of their own (issue #56).
     *
     * <p>Dawn and dusk are the sun crossing {@code twilightDegrees} below the horizon — how far is
     * a question the app does not answer for anybody, because the recognised answers differ and the
     * difference is twenty minutes and more. The afternoon event is the hour at which a standing
     * thing's shadow has grown by {@code shadowMultiple} times its own height beyond its noon
     * shadow; one and two are both in use, and again the choice is the user's. Everything here is
     * the position of the sun and nothing else: the app computes, it does not rule.
     */
    public static int utcMinute(int jdn, double latitude, double longitude, int event,
            int twilightDegrees, int shadowMultiple) {
        return utcMinute(jdn, latitude, longitude, event, twilightDegrees, shadowMultiple,
                DEFAULT_HIGH_RULE);
    }

    /**
     * The same, with what to do where the sun never reaches the twilight angle (issue #56).
     *
     * <p>The rule is asked only for dawn and dusk, and only on a day they do not happen. Everything
     * else is unchanged: where the sun does not rise or set at all there is no night to divide, and
     * nothing is invented from nothing.
     */
    public static int utcMinute(int jdn, double latitude, double longitude, int event,
            int twilightDegrees, int shadowMultiple, int highRule) {
        return utcMinute(jdn, latitude, longitude, event,
                SunRules.of(twilightDegrees, shadowMultiple, highRule));
    }

    /**
     * The event, by a set of rules (issue #56): the angles dawn, dusk and the evening are read at,
     * the shadow the afternoon is measured by, where the night ends, and what to do on a day the
     * sun never reaches the angle.
     */
    public static int utcMinute(int jdn, double latitude, double longitude, int event,
            SunRules rules) {
        int plain = plainUtcMinute(jdn, latitude, longitude, event, rules);
        if (plain != NONE || (event != DAWN && event != DUSK)) {
            return plain;
        }
        return substitute(jdn, latitude, longitude, event, rules);
    }

    /**
     * A dawn or dusk the sun never gave, worked out by the convention the user chose.
     *
     * The night is the one that touches the day: from its sunset to the next sunrise. Without both
     * of those — a sun that never sets, or never rises — there is no night to take a share of, and
     * only the nearest-day rule has anything left to offer.
     */
    private static int substitute(int jdn, double latitude, double longitude, int event,
            SunRules rules) {
        int rule = rules.highRule;
        if (rule == HIGH_NOTHING) {
            return NONE;
        }
        if (rule == HIGH_NEAREST_DAY) {
            for (int back = 1; back <= NEAREST_DAY_LIMIT; back++) {
                int had = plainUtcMinute(jdn - back, latitude, longitude, event, rules);
                if (had != NONE) {
                    // The clock time it had then, brought forward to this day.
                    return had;
                }
            }
            return NONE;
        }
        int sunset = plainUtcMinute(jdn, latitude, longitude, SUNSET, rules);
        int sunriseNext = plainUtcMinute(jdn + 1, latitude, longitude, SUNRISE, rules);
        int sunriseToday = plainUtcMinute(jdn, latitude, longitude, SUNRISE, rules);
        if (sunset == NONE || sunriseNext == NONE || sunriseToday == NONE) {
            return NONE;
        }
        double night = (sunriseNext + MINUTES_A_DAY) - sunset;
        double share;
        if (rule == HIGH_MIDDLE_OF_NIGHT) {
            share = night / 2;
        } else if (rule == HIGH_SEVENTH_OF_NIGHT) {
            share = night / 7;
        } else {
            // One sixtieth of the night for each degree, which is the share convention in use.
            int tenths = event == DAWN ? rules.dawnTenths
                    : (rules.duskTenths > 0 ? rules.duskTenths : rules.dawnTenths);
            share = night * (tenths / 10.0) / 60.0;
        }
        // Dawn is that much before the sunrise the night ends at; dusk that much after sunset.
        return (int) Math.round(event == DAWN ? sunriseToday - share : sunset + share);
    }

    private static int plainUtcMinute(int jdn, double latitude, double longitude, int event,
            SunRules rules) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude) || !isEvent(event)) {
            return NONE;
        }
        if (event == EVENING) {
            // Sunset itself, or the sun a few degrees down where the reckoning says so.
            return rules.eveningIsSunset()
                    ? plainUtcMinute(jdn, latitude, longitude, SUNSET, rules)
                    : angledUtcMinute(jdn, latitude, longitude, 90 + rules.eveningTenths / 10.0,
                            true);
        }
        if (event == DUSK && rules.duskByInterval()) {
            // The one reckoning that fixes dusk by the clock rather than by the sun: so many
            // minutes after the evening event, whatever the sky is doing.
            int evening = plainUtcMinute(jdn, latitude, longitude, EVENING, rules);
            return evening == NONE ? NONE : evening + rules.duskMinutesAfterEvening;
        }
        if (event == NIGHT_MIDDLE) {
            int sunset = plainUtcMinute(jdn, latitude, longitude, EVENING, rules);
            // The night runs to the next sunrise, or to the next dawn where the reckoning says so.
            int sunrise = rules.nightEndsAtDawn
                    ? utcMinute(jdn + 1, latitude, longitude, DAWN, rules)
                    : plainUtcMinute(jdn + 1, latitude, longitude, SUNRISE, rules);
            if (sunset == NONE || sunrise == NONE) {
                return NONE;
            }
            // Tomorrow's sunrise is counted from tomorrow's midnight; the night is what lies
            // between, and its middle is half of it.
            return (int) Math.round(sunset + ((sunrise + MINUTES_A_DAY) - sunset) / 2.0);
        }
        int dayOfYear = dayOfYear(jdn);
        double gamma = 2 * Math.PI / 365.0 * (dayOfYear - 1);
        double equation = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma) - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double declination = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double noon = 720 - 4 * longitude - equation;
        if (event == NOON) {
            return (int) Math.round(noon);
        }
        double lat = Math.toRadians(latitude);
        double zenith = zenithFor(event, rules, lat, declination);
        double cosHour = Math.cos(Math.toRadians(zenith)) / (Math.cos(lat) * Math.cos(declination))
                - Math.tan(lat) * Math.tan(declination);
        if (cosHour > 1 || cosHour < -1 || Double.isNaN(cosHour)) {
            return NONE;
        }
        double hour = Math.toDegrees(Math.acos(cosHour));
        boolean afternoon = event == SUNSET || event == DUSK || event == AFTERNOON_SHADOW;
        return (int) Math.round(afternoon ? noon + 4 * hour : noon - 4 * hour);
    }

    /** How far the sun's centre is from straight overhead at the moment the event happens. */
    private static double zenithFor(int event, SunRules rules, double lat, double declination) {
        if (event == DAWN) {
            return 90 + rules.dawnTenths / 10.0;
        }
        if (event == DUSK) {
            return 90 + rules.duskTenths / 10.0;
        }
        if (event == AFTERNOON_SHADOW) {
            // A stick of height 1 casts tan(zenith at noon) at noon; the event is when the shadow
            // has grown by the multiple, so its altitude is atan(1 / (multiple + noon shadow)).
            double noonShadow = Math.abs(Math.tan(lat - declination));
            double altitude = Math.atan(1.0 / (rules.shadowMultiple + noonShadow));
            return 90 - Math.toDegrees(altitude);
        }
        return HORIZON_ZENITH;
    }

    /**
     * The moment the sun's centre passes a zenith, before noon or after it. Used where an event is
     * an angle of its own rather than one of the named ones.
     */
    private static int angledUtcMinute(int jdn, double latitude, double longitude, double zenith,
            boolean afternoon) {
        int dayOfYear = dayOfYear(jdn);
        double gamma = 2 * Math.PI / 365.0 * (dayOfYear - 1);
        double equation = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma) - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double declination = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double lat = Math.toRadians(latitude);
        double cosHour = Math.cos(Math.toRadians(zenith)) / (Math.cos(lat) * Math.cos(declination))
                - Math.tan(lat) * Math.tan(declination);
        if (cosHour > 1 || cosHour < -1 || Double.isNaN(cosHour)) {
            return NONE;
        }
        double hour = Math.toDegrees(Math.acos(cosHour));
        double noon = 720 - 4 * longitude - equation;
        return (int) Math.round(afternoon ? noon + 4 * hour : noon - 4 * hour);
    }

    /**
     * The event in local minutes of day, folded into 0..1439, or {@link #NONE}.
     *
     * @param offsetMinutes the clock's offset from UTC on that day
     */
    public static int localMinute(int jdn, double latitude, double longitude, int event,
            int offsetMinutes) {
        return localMinute(jdn, latitude, longitude, event, offsetMinutes,
                DEFAULT_TWILIGHT_DEGREES, DEFAULT_SHADOW_MULTIPLE);
    }

    /** The same, with the conventions the events that need one are read by (issue #56). */
    public static int localMinute(int jdn, double latitude, double longitude, int event,
            int offsetMinutes, int twilightDegrees, int shadowMultiple) {
        return localMinute(jdn, latitude, longitude, event, offsetMinutes, twilightDegrees,
                shadowMultiple, DEFAULT_HIGH_RULE);
    }

    /** The same, with the rule for a day the sun never reaches the angle. */
    public static int localMinute(int jdn, double latitude, double longitude, int event,
            int offsetMinutes, int twilightDegrees, int shadowMultiple, int highRule) {
        return localMinute(jdn, latitude, longitude, event, offsetMinutes,
                SunRules.of(twilightDegrees, shadowMultiple, highRule));
    }

    /** The same, by a set of rules (issue #56). */
    public static int localMinute(int jdn, double latitude, double longitude, int event,
            int offsetMinutes, SunRules rules) {
        int utc = utcMinute(jdn, latitude, longitude, event, rules);
        if (utc == NONE) {
            return NONE;
        }
        return fold(utc + offsetMinutes);
    }

    private static final int MINUTES_A_DAY = Bell.MINUTES_A_DAY;

    /** A minute count folded into one day. */
    public static int fold(int minutes) {
        int m = minutes % Bell.MINUTES_A_DAY;
        return m < 0 ? m + Bell.MINUTES_A_DAY : m;
    }

    /** Which day of its year a civil day is, 1 for 1 January. */
    static int dayOfYear(int jdn) {
        return jdn - Gregorian.toJdn(Gregorian.year(jdn), 1, 1) + 1;
    }
}
