package com.reteclock.core;

/**
 * The East Asian lunisolar calendars — Chinese, Korean and Vietnamese — read from a baked table.
 *
 * These are the calendars behind Lunar New Year, Chuseok and Tet, and the ones a great many
 * birthdays and memorial days are kept in. They are also the only ones in this app that cannot be
 * computed from a rule: a month begins on the local day containing the astronomical new moon, and a
 * leap month is the first month of the year with no major solar term in it. See
 * {@link LunisolarTable} for why the astronomy is done on a desktop and not on the phone.
 *
 * A month has no name, only a number, and a leap month repeats the number of the month before it.
 * So the year has twelve or thirteen *positions*, and the number written on a position is not the
 * position — the sixth month and the leap sixth month are the sixth and seventh positions of that
 * year. Everything here that looks like an off-by-one is that.
 */
public final class Lunisolar {

    public static final int CHINA = 0;
    public static final int KOREA = 1;
    public static final int VIETNAM = 2;

    private Lunisolar() {
    }

    /** Whether the table can answer for a day at all. */
    public static boolean covers(int locale, int jdn) {
        int year = Gregorian.year(jdn);
        return LunisolarTable.covers(year - 1) && LunisolarTable.covers(year + 1);
    }

    public static int monthsInYear(int locale, int year) {
        return LunisolarTable.monthsInYear(locale, year);
    }

    /** How long the month at that position is: 29 days or 30. */
    public static int daysInMonth(int locale, int year, int position) {
        return LunisolarTable.daysInMonth(locale, year, position);
    }

    /** Whether the position holds the leap month. */
    public static boolean isLeapMonth(int locale, int year, int position) {
        return LunisolarTable.leapPosition(locale, year) == position;
    }

    /** The number written on the month at that position — not the position itself. */
    public static int monthNumber(int locale, int year, int position) {
        int leap = LunisolarTable.leapPosition(locale, year);
        if (leap == 0 || position < leap) {
            return position;
        }
        return position == leap ? leap - 1 : position - 1;
    }

    /** `M6`, or `L6` for the leap month that repeats the sixth. */
    public static String monthName(int locale, int year, int position) {
        return (isLeapMonth(locale, year, position) ? "L" : "M")
                + monthNumber(locale, year, position);
    }

    /** `6th month`, or `leap 6th month`. */
    public static String monthNameFull(int locale, int year, int position) {
        int number = monthNumber(locale, year, position);
        String ordinal = number + ordinalSuffix(number) + " month";
        return isLeapMonth(locale, year, position) ? "leap " + ordinal : ordinal;
    }

    private static String ordinalSuffix(int number) {
        if (number == 1) {
            return "st";
        }
        if (number == 2) {
            return "nd";
        }
        return number == 3 ? "rd" : "th";
    }

    /** The year, the position within it, and the day: the parts of a lunisolar date. */
    public static int[] parts(int locale, int jdn) {
        int year = Gregorian.year(jdn);
        while (LunisolarTable.newYear(locale, year) > jdn) {
            year--;
        }
        while (LunisolarTable.newYear(locale, year + 1) <= jdn) {
            year++;
        }
        int rest = jdn - LunisolarTable.newYear(locale, year);
        int position = 1;
        while (rest >= daysInMonth(locale, year, position)) {
            rest -= daysInMonth(locale, year, position);
            position++;
        }
        return new int[] {year, position, rest + 1};
    }

    /** The day number of a lunisolar date, given as a position rather than a month number. */
    public static int toJdn(int locale, int year, int position, int day) {
        int jdn = LunisolarTable.newYear(locale, year);
        for (int p = 1; p < position; p++) {
            jdn += daysInMonth(locale, year, p);
        }
        return jdn + day - 1;
    }
}
