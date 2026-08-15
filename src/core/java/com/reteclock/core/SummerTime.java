package com.reteclock.core;

/**
 * Summer time as a rule the user states, rather than a database the app ships.
 *
 * A time zone database is hundreds of kilobytes and a promise to keep it current — the promise the
 * platform broke on the phones this app is built for (RFC-0004). A rule is four numbers at each end
 * and does not go stale: somebody in Europe picks *Europe* once, and it keeps working after the next
 * country leaves the scheme, because it was never claiming to know about that country.
 *
 * Three things this gets right that a naive version does not:
 *
 * - **The southern hemisphere's summer wraps the year end**, so the start month can be later than
 *   the end month, and membership of the period is `t >= start || t < end` in that case.
 * - **The shift is not always an hour.** Lord Howe Island moves by thirty minutes, Troll station in
 *   Antarctica by two.
 * - **The transition is stated in the clock that is actually running at that moment.** Summer time
 *   begins at 02:00 *standard* time and ends at 02:00 *daylight* time, which is 01:00 standard —
 *   that is why the end is written as 01:00 here and not as 02:00. Comparing instants rather than
 *   local wall times also sidesteps the hour that happens twice and the hour that never happens.
 *
 * Pure Java: no android.*, no java.util.*.
 */
public final class SummerTime {

    /** No summer time; the offset is the offset all year. */
    public static final int PRESET_NONE = 0;
    /** The European Union's rule: last Sunday of March to last Sunday of October, 01:00 UTC. */
    public static final int PRESET_EUROPE = 1;
    /** The United States and Canada: second Sunday of March to first Sunday of November. */
    public static final int PRESET_NORTH_AMERICA = 2;
    /** The Australian pattern: first Sunday of October to first Sunday of April. */
    public static final int PRESET_SOUTHERN = 3;
    /** Anything else, stated by the user. */
    public static final int PRESET_CUSTOM = 4;

    /** As an ordinal: the last such weekday of the month rather than the n-th. */
    public static final int LAST = 0;

    private final int startMonth;
    private final int startWeekday;
    private final int startOrdinal;
    private final int startMinutes;
    private final boolean startInUtc;
    private final int endMonth;
    private final int endWeekday;
    private final int endOrdinal;
    private final int endMinutes;
    private final boolean endInUtc;
    private final int amountMinutes;

    private SummerTime(int startMonth, int startWeekday, int startOrdinal, int startMinutes,
            boolean startInUtc, int endMonth, int endWeekday, int endOrdinal, int endMinutes,
            boolean endInUtc, int amountMinutes) {
        this.startMonth = startMonth;
        this.startWeekday = startWeekday;
        this.startOrdinal = startOrdinal;
        this.startMinutes = startMinutes;
        this.startInUtc = startInUtc;
        this.endMonth = endMonth;
        this.endWeekday = endWeekday;
        this.endOrdinal = endOrdinal;
        this.endMinutes = endMinutes;
        this.endInUtc = endInUtc;
        this.amountMinutes = amountMinutes;
    }

    /** One of the presets; anything unrecognised means none, which is the safe answer. */
    public static SummerTime preset(int which) {
        switch (which) {
            case PRESET_EUROPE:
                // Last Sunday of March to last Sunday of October, both at 01:00 UTC — the whole
                // union turns together, which is why this one is stated in UTC and the others local.
                return new SummerTime(3, 0, LAST, 60, true, 10, 0, LAST, 60, true, 60);
            case PRESET_NORTH_AMERICA:
                // Second Sunday of March at 02:00 standard, first Sunday of November at 02:00
                // daylight — written as 01:00 standard, which is the same instant.
                return new SummerTime(3, 0, 2, 120, false, 11, 0, 1, 60, false, 60);
            case PRESET_SOUTHERN:
                // First Sunday of October at 02:00 standard, first Sunday of April at 03:00
                // daylight — again, 02:00 standard.
                return new SummerTime(10, 0, 1, 120, false, 4, 0, 1, 120, false, 60);
            default:
                return null;
        }
    }

    /**
     * A rule the user stated.
     *
     * @param month     1..12
     * @param weekday   0 for Sunday
     * @param ordinal   1..4 for the n-th such weekday, or {@link #LAST}
     * @param minutes   minutes past midnight, in standard local time
     * @param amount    30, 60 or 120 minutes
     */
    public static SummerTime custom(int startMonth, int startWeekday, int startOrdinal,
            int startMinutes, int endMonth, int endWeekday, int endOrdinal, int endMinutes,
            int amount) {
        return new SummerTime(startMonth, startWeekday, startOrdinal, startMinutes, false,
                endMonth, endWeekday, endOrdinal, endMinutes, false, amount);
    }

    /** What this rule adds to the standard offset at that instant: 0, or the shift. */
    public int amountAt(long epochMillis, int standardOffsetMinutes) {
        int year = Gregorian.year(CivilTime.jdnOf(epochMillis, standardOffsetMinutes));
        long start = transition(year, startMonth, startWeekday, startOrdinal, startMinutes,
                startInUtc ? 0 : standardOffsetMinutes);
        long end = transition(year, endMonth, endWeekday, endOrdinal, endMinutes,
                endInUtc ? 0 : standardOffsetMinutes);
        boolean inside = start < end
                ? epochMillis >= start && epochMillis < end
                : epochMillis >= start || epochMillis < end;
        return inside ? amountMinutes : 0;
    }

    /** The offset in force at that instant: the standard one, plus summer time if it applies. */
    public static int offsetAt(long epochMillis, int standardOffsetMinutes, SummerTime rule) {
        return rule == null
                ? standardOffsetMinutes
                : standardOffsetMinutes + rule.amountAt(epochMillis, standardOffsetMinutes);
    }

    /** How much this rule shifts the clock when it is in force. */
    public int amount() {
        return amountMinutes;
    }

    /** The instant of one transition in a given year, at a given offset. */
    private static long transition(int year, int month, int weekday, int ordinal, int minutes,
            int offsetMinutes) {
        int jdn;
        if (ordinal == LAST) {
            int last = Gregorian.toJdn(year, month, Gregorian.daysIn(year, month));
            jdn = last - (CivilTime.weekday(last) - weekday + 7) % 7;
        } else {
            int first = Gregorian.toJdn(year, month, 1);
            jdn = first + (weekday - CivilTime.weekday(first) + 7) % 7 + 7 * (ordinal - 1);
        }
        return CivilTime.epochMillisOf(jdn, 0, 0, 0, offsetMinutes) + minutes * 60000L;
    }
}
