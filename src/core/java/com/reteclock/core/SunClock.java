package com.reteclock.core;

/**
 * Where the sun is reckoned from, and in which local time (issue #55, RFC-0015).
 *
 * <p>The place is a latitude and a longitude the user gave, typed or picked from the city list. The
 * time is the clock's own offset on that day — the app's zone and summer time — so a bell at sunrise
 * rings at the minute the clock shows for sunrise, just as a bell at seven rings when it shows seven.
 * A city says nothing about the offset, and is not asked to: the offset stays the user's setting.
 */
public final class SunClock {

    /** No place set: a bell that follows the sun cannot ring. */
    public static final SunClock NONE = new SunClock(Double.NaN, Double.NaN, null);

    public final double latitude;
    public final double longitude;
    private final WakeSchedule.TimeBase base;

    public SunClock(double latitude, double longitude, WakeSchedule.TimeBase base) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.base = base;
    }

    /** Whether a place is set and makes sense. */
    public boolean isSet() {
        return base != null && validLatitude(latitude) && validLongitude(longitude);
    }

    public static boolean validLatitude(double value) {
        return !Double.isNaN(value) && value >= -90 && value <= 90;
    }

    public static boolean validLongitude(double value) {
        return !Double.isNaN(value) && value >= -180 && value <= 180;
    }

    /** The clock's offset on a civil day, asked at noon so a change at 02:00 counts for that day. */
    public int offsetOn(int jdn) {
        if (base == null) {
            return 0;
        }
        return base.offsetMinutesAt(CivilTime.epochMillisOf(jdn, 12, 0, 0, 0));
    }

    /** The event's local minute on that day, or {@link SunTimes#NONE}. */
    public int localMinute(int jdn, int event) {
        if (!isSet()) {
            return SunTimes.NONE;
        }
        return SunTimes.localMinute(jdn, latitude, longitude, event, offsetOn(jdn));
    }
}
