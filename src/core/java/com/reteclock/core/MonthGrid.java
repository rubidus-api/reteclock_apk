package com.reteclock.core;

/**
 * A month as the rows and columns a calendar is drawn in.
 *
 * Six rows of seven, which is the most any month can need — a 31-day month beginning on the last
 * day of a week reaches into a sixth. Always six, so the grid does not change height as the months
 * are paged through and the clock beneath it does not shuffle up and down.
 *
 * The weekday of the first is worked out here rather than asked of `java.util.Calendar`: this is
 * arithmetic, it is the same on every device and in every locale, and doing it here means the whole
 * thing can be tested away from Android.
 */
public final class MonthGrid {

    public static final int ROWS = 6;
    public static final int COLUMNS = 7;

    /** `Aug 2026`. */
    public static final int HEADER_NAME = 0;
    /** `2026-08`. */
    public static final int HEADER_NUMBERS = 1;

    private static final String[] MONTH_NAMES = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    };

    /** Sunday first, which is how the rest of the clock's dates read. */
    private static final String[] DAYS_FROM_SUNDAY = {
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
    };

    private final int year;
    private final int month;
    private final boolean weekStartsMonday;
    private final int[] cells;

    private MonthGrid(int year, int month, boolean weekStartsMonday, int[] cells) {
        this.year = year;
        this.month = month;
        this.weekStartsMonday = weekStartsMonday;
        this.cells = cells;
    }

    /**
     * @param year  the full year, 2026 and not 26
     * @param month 1 for January through 12 for December
     */
    public static MonthGrid of(int year, int month, boolean weekStartsMonday) {
        int safeMonth = month < 1 ? 1 : month > 12 ? 12 : month;
        int lead = column(dayOfWeek(year, safeMonth, 1), weekStartsMonday);
        int days = daysIn(year, safeMonth);

        int[] cells = new int[ROWS * COLUMNS];
        for (int day = 1; day <= days; day++) {
            cells[lead + day - 1] = day;
        }
        return new MonthGrid(year, safeMonth, weekStartsMonday, cells);
    }

    /** The month one step back, rolling into the previous year when it has to. */
    public MonthGrid previous() {
        return month == 1 ? of(year - 1, 12, weekStartsMonday) : of(year, month - 1, weekStartsMonday);
    }

    /** And one step on. */
    public MonthGrid next() {
        return month == 12 ? of(year + 1, 1, weekStartsMonday) : of(year, month + 1, weekStartsMonday);
    }

    public int year() {
        return year;
    }

    public int month() {
        return month;
    }

    /** The day in a cell, or 0 where the grid runs before the first or past the last. */
    public int dayAt(int row, int column) {
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return 0;
        }
        return cells[row * COLUMNS + column];
    }

    /** Whether a given date falls in this month — what marks today out from every other day. */
    public boolean holds(int otherYear, int otherMonth) {
        return otherYear == year && otherMonth == month;
    }

    /** `Aug 2026` or `2026-08`, as the user chose. */
    public String header(int style) {
        if (style == HEADER_NUMBERS) {
            StringBuilder out = new StringBuilder(7).append(year).append('-');
            if (month < 10) {
                out.append('0');
            }
            return out.append(month).toString();
        }
        return MONTH_NAMES[month - 1] + " " + year;
    }

    /** The seven column headings, in the order this grid puts them. */
    public String[] weekdayNames() {
        return weekdayNames(weekStartsMonday);
    }

    public static String[] weekdayNames(boolean weekStartsMonday) {
        String[] out = new String[COLUMNS];
        for (int i = 0; i < COLUMNS; i++) {
            out[i] = DAYS_FROM_SUNDAY[(i + (weekStartsMonday ? 1 : 0)) % COLUMNS];
        }
        return out;
    }

    /** Where a weekday sits, given which day the week is taken to start on. */
    private static int column(int dayOfWeek, boolean weekStartsMonday) {
        return weekStartsMonday ? (dayOfWeek + 6) % 7 : dayOfWeek;
    }

    /**
     * The day of the week, 0 for Sunday — Sakamoto's method.
     *
     * The table holds how far into the year each month's first day is, modulo seven, and January
     * and February are counted as belonging to the year before so that the leap day lands at the
     * end where the correction can be applied once.
     */
    public static int dayOfWeek(int year, int month, int day) {
        int[] offsets = {0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4};
        int y = month < 3 ? year - 1 : year;
        return (y + y / 4 - y / 100 + y / 400 + offsets[month - 1] + day) % 7;
    }

    /** How many days a month has, leap years included. */
    public static int daysIn(int year, int month) {
        switch (month) {
            case 2:
                return leapYear(year) ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /** A year divisible by four, except centuries, except those divisible by four hundred. */
    public static boolean leapYear(int year) {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
    }
}
