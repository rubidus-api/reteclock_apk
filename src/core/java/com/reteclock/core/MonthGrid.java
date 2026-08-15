package com.reteclock.core;

/**
 * A month as the rows and columns a calendar is drawn in.
 *
 * Six rows of seven, which is the most any month can need — a 31-day month beginning on the last
 * day of a week reaches into a sixth. Always six, so the grid does not change height as the months
 * are paged through and the clock beneath it does not shuffle up and down.
 *
 * The grid holds the **day number of the first of the month** rather than a year and a month, and
 * pages by stepping over whole months. That is what lets one class draw fourteen calendars: a year
 * may have thirteen months, the extra one may be inserted in the middle, and a Japanese era year is
 * ambiguous on its own — none of which matters to something counting days.
 *
 * The weekday of the first is arithmetic here rather than asked of `java.util.Calendar`: it is the
 * same on every device and in every locale, and doing it here means the whole thing can be tested
 * away from Android.
 */
public final class MonthGrid {

    public static final int ROWS = 6;
    public static final int COLUMNS = 7;

    /** `Aug 2026`. */
    public static final int HEADER_NAME = 0;
    /** `2026-08`. */
    public static final int HEADER_NUMBERS = 1;

    /** Sunday first, which is how the rest of the clock's dates read. */
    private static final String[] DAYS_FROM_SUNDAY = {
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
    };

    private final int system;
    private final int year;
    private final int month;
    private final int firstJdn;
    private final int days;
    private final int weekStart;
    private final int nameStyle;
    private final int weekdayStyle;
    private final int[] cells;

    private MonthGrid(int system, int year, int month, int firstJdn, int days, int weekStart,
            int nameStyle, int weekdayStyle, int[] cells) {
        this.weekdayStyle = weekdayStyle;
        this.nameStyle = nameStyle;
        this.system = system;
        this.year = year;
        this.month = month;
        this.firstJdn = firstJdn;
        this.days = days;
        this.weekStart = weekStart;
        this.cells = cells;
    }

    /**
     * The month containing a given day, in a given calendar.
     *
     * @param weekStart the day the week is taken to begin on: 0 for Sunday, 1 for Monday, 6 for
     *                  Saturday — which is where Iran and Israel start theirs.
     */
    public static MonthGrid ofDay(int system, int jdn, int weekStart) {
        return ofDay(system, jdn, weekStart, 0);
    }

    /** The same, with the months spelled in one of the calendar's styles. */
    public static MonthGrid ofDay(int system, int jdn, int weekStart, int nameStyle) {
        return ofDay(system, jdn, weekStart, nameStyle, 0);
    }

    /** And with the column headings in English or in the calendar's own weekday names. */
    public static MonthGrid ofDay(int system, int jdn, int weekStart, int nameStyle,
            int weekdayStyle) {
        CalendarDate date = Calendars.dateOf(system, jdn);
        int first = jdn - date.day + 1;
        int length = Calendars.daysInMonth(date.system, date.year, date.month);
        int lead = column(CivilTime.weekday(first), weekStart);

        int[] cells = new int[ROWS * COLUMNS];
        for (int day = 1; day <= length && lead + day - 1 < cells.length; day++) {
            cells[lead + day - 1] = day;
        }
        return new MonthGrid(date.system, date.year, date.month, first, length, weekStart,
                nameStyle, weekdayStyle, cells);
    }

    /** The Gregorian month, as the clock has always asked for it. */
    public static MonthGrid of(int year, int month, boolean weekStartsMonday) {
        int safeMonth = month < 1 ? 1 : month > 12 ? 12 : month;
        return ofDay(Calendars.GREGORIAN, Gregorian.toJdn(year, safeMonth, 1),
                weekStartsMonday ? 1 : 0);
    }

    /** The Gregorian month, with the week starting on any day. */
    public static MonthGrid of(int year, int month, int weekStart) {
        int safeMonth = month < 1 ? 1 : month > 12 ? 12 : month;
        return ofDay(Calendars.GREGORIAN, Gregorian.toJdn(year, safeMonth, 1), weekStart);
    }

    /** The month one step back — the month the day before this one belongs to. */
    public MonthGrid previous() {
        return ofDay(system, firstJdn - 1, weekStart, nameStyle, weekdayStyle);
    }

    /** And one step on. */
    public MonthGrid next() {
        return ofDay(system, firstJdn + days, weekStart, nameStyle, weekdayStyle);
    }

    public int system() {
        return system;
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    /** The day number of the first of this month, which is how the grid is paged. */
    public int firstJdn() {
        return firstJdn;
    }

    /** How many days this month has. */
    public int length() {
        return days;
    }

    /** The day in a cell, or 0 where the grid runs before the first or past the last. */
    public int dayAt(int row, int column) {
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return 0;
        }
        return cells[row * COLUMNS + column];
    }

    /** Whether a given day falls in this month — what marks today out from every other day. */
    public boolean holdsDay(int jdn) {
        return jdn >= firstJdn && jdn < firstJdn + days;
    }

    /** Whether a given date falls in this month, as the Gregorian callers have always asked. */
    public boolean holds(int otherYear, int otherMonth) {
        return otherYear == year && otherMonth == month;
    }

    /** `Mor 1405` or `1405-05`, as the user chose. */
    public String header(int style) {
        String yearText = Calendars.yearText(system, firstJdn);
        if (style == HEADER_NUMBERS) {
            return yearText + "-" + (month < 10 ? "0" + month : Integer.toString(month));
        }
        return Calendars.monthName(system, year, month, nameStyle) + " " + yearText;
    }

    /** The seven column headings, in the order this grid puts them. */
    public String[] weekdayNames() {
        String[] names = Calendars.weekdayNames(system, weekdayStyle);
        String[] out = new String[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            out[i] = names[(i + weekStart) % COLUMNS];
        }
        return out;
    }

    public static String[] weekdayNames(boolean weekStartsMonday) {
        return weekdayNames(weekStartsMonday ? 1 : 0);
    }

    public static String[] weekdayNames(int weekStart) {
        String[] out = new String[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            out[i] = DAYS_FROM_SUNDAY[(i + weekStart) % COLUMNS];
        }
        return out;
    }

    /** Where a weekday sits, given which day the week is taken to start on. */
    private static int column(int dayOfWeek, int weekStart) {
        return (dayOfWeek - weekStart + COLUMNS) % COLUMNS;
    }

    /** The day of the week, 0 for Sunday — kept for the callers that have always used it. */
    public static int dayOfWeek(int year, int month, int day) {
        return CivilTime.weekday(Gregorian.toJdn(year, month, day));
    }

    /** How many days a Gregorian month has, leap years included. */
    public static int daysIn(int year, int month) {
        return Gregorian.daysIn(year, month);
    }

    /** A year divisible by four, except centuries, except those divisible by four hundred. */
    public static boolean leapYear(int year) {
        return Gregorian.leapYear(year);
    }
}
