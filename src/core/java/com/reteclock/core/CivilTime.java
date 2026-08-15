package com.reteclock.core;

/**
 * An instant, read as a civil date and a time of day.
 *
 * The platform is asked for one thing only — the number of milliseconds since the Unix epoch, which
 * is UTC and owes nothing to the time zone database — and everything after that is arithmetic this
 * project owns. That matters because the floor here is Android 4.4, whose zone database stopped
 * being updated years ago and is now wrong in every country that has changed or abolished summer
 * time since (RFC-0004).
 *
 * The date is carried as a Julian day number rather than a year, a month and a day, because a JDN
 * is what every calendar in {@link Calendars} converts from. One seam, fourteen calendars.
 *
 * Pure Java: no android.*, no java.util.Calendar, no floating point.
 */
public final class CivilTime {

    /** The Julian day number of 1 January 1970, where the Unix epoch begins. */
    public static final int JDN_UNIX_EPOCH = 2440588;

    private static final long MS_PER_DAY = 86400000L;

    /** The first day this project promises to be right about: 1 January 1900. */
    public static final int FIRST_JDN = 2415021;

    /** And the last: 31 December 2200. */
    public static final int LAST_JDN = 2524958;

    /** The Julian day number of the civil date. */
    public final int jdn;
    /** Hour of the day, 0..23. */
    public final int hour;
    /** Minute, 0..59. */
    public final int minute;
    /** Second, 0..59. */
    public final int second;
    /** Millisecond within the second, 0..999 — the timer's readouts want it. */
    public final int millis;

    private CivilTime(int jdn, int hour, int minute, int second, int millis) {
        this.jdn = jdn;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.millis = millis;
    }

    /**
     * Reads an instant at a given offset from UTC.
     *
     * @param epochMillis   milliseconds since 1970-01-01T00:00:00Z, as the platform gives them
     * @param offsetMinutes minutes east of UTC, so Seoul is 540 and Kathmandu 345. Summer time, if
     *                      any, has already been folded in by {@link SummerTime}.
     */
    public static CivilTime of(long epochMillis, int offsetMinutes) {
        long local = epochMillis + offsetMinutes * 60000L;
        long day = floorDiv(local, MS_PER_DAY);
        int rest = (int) (local - day * MS_PER_DAY);
        return new CivilTime((int) (day + JDN_UNIX_EPOCH),
                rest / 3600000, rest / 60000 % 60, rest / 1000 % 60, rest % 1000);
    }

    /** Just the day, for the callers that only want to know the date. */
    public static int jdnOf(long epochMillis, int offsetMinutes) {
        return (int) (floorDiv(epochMillis + offsetMinutes * 60000L, MS_PER_DAY) + JDN_UNIX_EPOCH);
    }

    /** The other direction: the instant a civil date and time stands for, at that offset. */
    public static long epochMillisOf(int jdn, int hour, int minute, int second, int offsetMinutes) {
        return (jdn - (long) JDN_UNIX_EPOCH) * MS_PER_DAY
                + hour * 3600000L + minute * 60000L + second * 1000L
                - offsetMinutes * 60000L;
    }

    /** Whether a day falls inside the span this project promises to be right about. */
    public static boolean inSpan(int jdn) {
        return jdn >= FIRST_JDN && jdn <= LAST_JDN;
    }

    /**
     * The day of the week, 0 for Sunday.
     *
     * True for every calendar in the app, because they all ride the same seven-day week: the JDN
     * counts days, and the remainder is the weekday. Julian day 0 was a Monday, so the +1 puts
     * Sunday at zero and matches what the rest of the clock already means by a weekday.
     */
    public static int weekday(int jdn) {
        return (jdn + 1) % 7;
    }

    /**
     * Floor division, which is the whole trick.
     *
     * A third of the guaranteed span is before 1970, so the millisecond count is negative there, and
     * Java's {@code /} truncates towards zero — which would put the last hours of 1899 in the wrong
     * day. {@code Math.floorDiv} is API 24, so it is written out.
     */
    private static long floorDiv(long value, long divisor) {
        long q = value / divisor;
        if (value % divisor != 0 && ((value < 0) != (divisor < 0))) {
            q--;
        }
        return q;
    }
}
