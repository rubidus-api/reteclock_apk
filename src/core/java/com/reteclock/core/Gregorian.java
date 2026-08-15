package com.reteclock.core;

/**
 * The Gregorian calendar as arithmetic on Julian day numbers.
 *
 * Every other calendar in this app converts through a JDN, so this is the bridge between the day
 * numbers the clock counts in and the dates people read. The formulae are the standard ones and are
 * pure integer arithmetic — no floating point, nothing that behaves differently on one device.
 *
 * {@link MonthGrid} keeps its own `dayOfWeek` and `daysIn` for the callers and tests that have
 * always used them; they answer the same as these.
 */
public final class Gregorian {

    private Gregorian() {
    }

    /** The Julian day number of a Gregorian date. */
    public static int toJdn(int year, int month, int day) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
    }

    /** The year of a Julian day number. */
    public static int year(int jdn) {
        return parts(jdn)[0];
    }

    /** The month, 1..12. */
    public static int month(int jdn) {
        return parts(jdn)[1];
    }

    /** The day of the month. */
    public static int day(int jdn) {
        return parts(jdn)[2];
    }

    /** Year, month and day together, which is what callers usually want. */
    public static int[] parts(int jdn) {
        int a = jdn + 32044;
        int b = (4 * a + 3) / 146097;
        int c = a - 146097 * b / 4;
        int d = (4 * c + 3) / 1461;
        int e = c - 1461 * d / 4;
        int m = (5 * e + 2) / 153;
        return new int[] {
            100 * b + d - 4800 + m / 10,
            m + 3 - 12 * (m / 10),
            e - (153 * m + 2) / 5 + 1,
        };
    }

    /** A year divisible by four, except centuries, except those divisible by four hundred. */
    public static boolean leapYear(int year) {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
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
}
