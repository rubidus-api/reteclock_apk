package com.reteclock.core;

/**
 * A date, in whichever calendar produced it.
 *
 * Deliberately more than a year, a month and a day. Three of the calendars here have years of
 * thirteen months, and in two of those the extra month is inserted somewhere in the middle rather
 * than added at the end — so a caller that wants to page through months, lay out a grid or write a
 * header has to be able to ask the date how many months its year has and whether this one is the
 * intercalary one. Assuming twelve is the single most likely way to be wrong.
 *
 * {@code system} is the calendar that actually answered, which is not always the one that was asked
 * for: outside 1900–2200 every calendar defers to the Gregorian one (RFC-0003, D15), and a caller
 * writing a header needs to know that happened.
 */
public final class CalendarDate {

    /** The calendar that produced this date — one of the constants in {@link Calendars}. */
    public final int system;
    /** The year, as that calendar counts them. */
    public final int year;
    /** The month, 1 to {@link #monthsInYear}. */
    public final int month;
    /** The day of the month. */
    public final int day;
    /** How many months this year has: twelve, or thirteen. */
    public final int monthsInYear;
    /** Whether this month is the intercalary one — a leap month, or Ethiopian Pagume. */
    public final boolean leapMonth;

    public CalendarDate(int system, int year, int month, int day, int monthsInYear,
            boolean leapMonth) {
        this.system = system;
        this.year = year;
        this.month = month;
        this.day = day;
        this.monthsInYear = monthsInYear;
        this.leapMonth = leapMonth;
    }

    /** The common case: a twelve-month year with nothing intercalary about it. */
    public CalendarDate(int system, int year, int month, int day) {
        this(system, year, month, day, 12, false);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CalendarDate)) {
            return false;
        }
        CalendarDate that = (CalendarDate) other;
        return system == that.system && year == that.year && month == that.month
                && day == that.day && monthsInYear == that.monthsInYear
                && leapMonth == that.leapMonth;
    }

    @Override
    public int hashCode() {
        return ((system * 31 + year) * 31 + month) * 31 + day;
    }

    @Override
    public String toString() {
        return Calendars.name(system) + " " + year + "-" + month + "-" + day
                + (leapMonth ? " (leap)" : "");
    }
}
