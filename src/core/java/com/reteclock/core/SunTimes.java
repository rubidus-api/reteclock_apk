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

    private SunTimes() {
    }

    /**
     * The event, in minutes from UTC midnight of the civil day {@code jdn}, or {@link #NONE}.
     *
     * The answer can lie outside 0..1440 for a place far from Greenwich; the caller adds its offset
     * and folds. {@code event} is {@link #SUNRISE} or {@link #SUNSET}.
     */
    public static int utcMinute(int jdn, double latitude, double longitude, int event) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return NONE;
        }
        int dayOfYear = dayOfYear(jdn);
        double gamma = 2 * Math.PI / 365.0 * (dayOfYear - 1);
        double equation = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma) - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));
        double declination = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma) + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double lat = Math.toRadians(latitude);
        double cosHour = Math.cos(Math.toRadians(90.833)) / (Math.cos(lat) * Math.cos(declination))
                - Math.tan(lat) * Math.tan(declination);
        if (cosHour > 1 || cosHour < -1 || Double.isNaN(cosHour)) {
            return NONE;
        }
        double hour = Math.toDegrees(Math.acos(cosHour));
        double minutes = event == SUNSET
                ? 720 - 4 * (longitude - hour) - equation
                : 720 - 4 * (longitude + hour) - equation;
        return (int) Math.round(minutes);
    }

    /**
     * The event in local minutes of day, folded into 0..1439, or {@link #NONE}.
     *
     * @param offsetMinutes the clock's offset from UTC on that day
     */
    public static int localMinute(int jdn, double latitude, double longitude, int event,
            int offsetMinutes) {
        int utc = utcMinute(jdn, latitude, longitude, event);
        if (utc == NONE) {
            return NONE;
        }
        return fold(utc + offsetMinutes);
    }

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
