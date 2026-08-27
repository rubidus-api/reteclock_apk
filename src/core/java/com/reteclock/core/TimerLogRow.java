package com.reteclock.core;

/**
 * One line of the timer log: a run that happened, and what it came to.
 *
 * The columns are, in order: the year, month, day, hour, minute and second the run began on the
 * phone's own calendar and clock; the timer's name; how many intervals it got through; how many
 * seconds it ran for, not counting time paused; and the same starting moment as a Unix time.
 *
 * The date is six columns rather than one string because a spreadsheet can group by a column and
 * cannot group by a format it has to be taught. The Unix time is written as well, and is not a
 * duplicate of them: the local columns say what the person saw, and that reading is ambiguous for
 * one hour every autumn and belongs to whichever country the phone was in. Neither can be recovered
 * from the other afterwards, so both are kept.
 *
 * See RFC-0006.
 */
public final class TimerLogRow {

    /** The header written at the top of every file, so a file can be read without this class. */
    public static final String HEADER =
            "year,month,day,hour,minute,second,timer,intervals,seconds,unix";

    public final int year;
    public final int month;
    public final int day;
    public final int hour;
    public final int minute;
    public final int second;
    public final String timer;
    /** How many intervals the run finished. A repeating preset keeps counting across the rounds. */
    public final int intervals;
    /** How long it ran, in whole seconds, with any time paused already taken out. */
    public final long seconds;
    /**
     * When it began, in seconds since 1970.
     *
     * Plain division, not a floor: `Math.floorDiv` arrived in Android 7 and this app runs on
     * Android 2.3, and the two differ only for moments before 1970, which no phone's clock reaches.
     */
    public final long unix;

    public TimerLogRow(int year, int month, int day, int hour, int minute, int second,
            String timer, int intervals, long seconds, long unix) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.timer = timer == null ? "" : timer;
        this.intervals = Math.max(intervals, 0);
        this.seconds = Math.max(seconds, 0L);
        this.unix = unix;
    }

    /**
     * The row for a run that has just ended.
     *
     * @param preset          what was running; its name and its intervals are read from it
     * @param startEpochMs    when the run began, by the wall clock
     * @param offsetMinutes   how far the phone's own time is from UTC at that moment
     * @param elapsedMs       how long it ran, pauses already excluded
     */
    public static TimerLogRow of(TimerPreset preset, long startEpochMs, int offsetMinutes,
            long elapsedMs) {
        CivilTime civil = CivilTime.of(startEpochMs, offsetMinutes);
        int[] date = Gregorian.parts(civil.jdn);
        return new TimerLogRow(date[0], date[1], date[2], civil.hour, civil.minute, civil.second,
                preset == null ? "" : preset.name,
                intervalsDone(preset, elapsedMs),
                Math.max(0L, elapsedMs) / 1000L,
                startEpochMs / 1000L);
    }

    /**
     * How many intervals a run of this length got through.
     *
     * Whole ones only. Somebody who stops half way through the fourth round did three, and counting
     * the one they abandoned would make the log say they had done work they had not. A preset that
     * repeats keeps counting round after round, because ten rounds of four intervals is forty
     * intervals to the person who did them.
     */
    public static int intervalsDone(TimerPreset preset, long elapsedMs) {
        if (preset == null || preset.intervals.isEmpty() || elapsedMs <= 0L) {
            return 0;
        }
        long total = preset.totalMs();
        long left = elapsedMs;
        int done = 0;
        if (preset.loops && total > 0L) {
            long rounds = left / total;
            // A run measured in days would overflow the count before it overflowed the clock.
            long counted = rounds * preset.intervals.size();
            if (counted > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            done = (int) counted;
            left -= rounds * total;
        }
        for (int i = 0; i < preset.intervals.size(); i++) {
            long length = preset.intervals.get(i).lengthMs;
            if (left < length) {
                break;
            }
            left -= length;
            done++;
        }
        return done;
    }

    /** The line as it is written to the file, without its ending. */
    public String line() {
        StringBuilder out = new StringBuilder();
        out.append(year).append(',').append(month).append(',').append(day).append(',')
                .append(hour).append(',').append(minute).append(',').append(second).append(',')
                .append(escaped(timer)).append(',')
                .append(intervals).append(',').append(seconds).append(',').append(unix);
        return out.toString();
    }

    /**
     * A name as CSV holds it.
     *
     * Timers are named by the person using them, and "Bread, second rise" is a perfectly good name
     * for one. A name with a comma, a quote or a line break in it is wrapped in quotes with its own
     * quotes doubled — the rule every spreadsheet reads — and a line break is turned into a space
     * rather than carried, because a row that spans two lines is a row that a `tail` or a size trim
     * can cut in half.
     */
    public static String escaped(String name) {
        String value = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ');
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
