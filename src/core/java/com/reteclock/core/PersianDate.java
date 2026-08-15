package com.reteclock.core;

/**
 * The Solar Hijri calendar — Iran's, and the reason this whole feature exists (issue #23).
 *
 * Same epoch as the Islamic lunar calendar, the Hijra of 622, but it follows the sun: the year
 * begins at the March equinox as observed at Iranian standard time, so Nowruz falls on 20 or 21
 * March. The first six months have 31 days, the next five 30, and Esfand 29 or 30.
 *
 * The leap rule is the interesting part, because in law there is none — a year is long when the
 * interval between two Nowruzes happens to be 366 days. This uses the 33-year cycle, which was
 * checked two ways before it was written down (RFC-0003, D2): against the eighteen published Nowruz
 * dates of 1393–1410 AP, and against the computed March equinox under the noon rule for every year
 * from 1800 to 2200 — 401 years, no disagreements.
 */
public final class PersianDate {

    /** Julian day number of 1 Farvardin 1. */
    private static final int EPOCH = 1948320;

    /** The years of a 33-year cycle that are long. */
    private static final int[] LEAPS = {1, 5, 9, 13, 17, 22, 26, 30};

    /** Three letters, as issue #23 supplied them. */
    public static final String[] MONTHS = {
        "Far", "Ord", "Kho", "Tir", "Mor", "Sha", "Meh", "Aba", "Aza", "Dey", "Bah", "Esf",
    };

    /** The names in full, for the settings screen. */
    public static final String[] MONTHS_FULL = {
        "Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar",
        "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand",
    };

    /**
     * The same calendar as Afghanistan names it.
     *
     * Identical arithmetic, identical dates — Nowruz is Nowruz — but the months are the Arabic
     * zodiac names rather than the Persian ones: Hamal where Iran says Farvardin. Around forty
     * million people read the calendar this way, and it was official there until 2022. The first
     * version of this feature listed it as "a name-array swap if ever asked for"; this is that.
     */
    public static final String[] MONTHS_AFGHAN = {
        "Ham", "Saw", "Jaw", "Sar", "Asa", "Sun", "Miz", "Aqr", "Qaw", "Jad", "Dal", "Hut",
    };

    public static final String[] MONTHS_AFGHAN_FULL = {
        "Hamal", "Sawr", "Jawza", "Saratan", "Asad", "Sunbula",
        "Mizan", "Aqrab", "Qaws", "Jadi", "Dalw", "Hut",
    };

    private PersianDate() {
    }

    public static boolean leapYear(int year) {
        int r = year % 33;
        for (int leap : LEAPS) {
            if (r == leap) {
                return true;
            }
        }
        return false;
    }

    public static int daysIn(int year, int month) {
        if (month <= 6) {
            return 31;
        }
        return month <= 11 ? 30 : (leapYear(year) ? 30 : 29);
    }

    /** How many of the years before this one were long. */
    private static int leapsBefore(int year) {
        int n = year - 1;
        int whole = n / 33;
        int rest = n % 33;
        int count = whole * 8;
        for (int leap : LEAPS) {
            if (leap <= rest) {
                count++;
            }
        }
        return count;
    }

    public static int toJdn(int year, int month, int day) {
        int days = (year - 1) * 365 + leapsBefore(year);
        for (int m = 1; m < month; m++) {
            days += m <= 6 ? 31 : 30;
        }
        return EPOCH + days + day - 1;
    }

    /** Year, month and day of a Julian day number. */
    public static int[] parts(int jdn) {
        int year = (jdn - EPOCH) / 366 + 1;
        while (toJdn(year, 1, 1) > jdn) {
            year--;
        }
        while (toJdn(year + 1, 1, 1) <= jdn) {
            year++;
        }
        int rest = jdn - toJdn(year, 1, 1);
        int month = 1;
        while (rest >= daysIn(year, month)) {
            rest -= daysIn(year, month);
            month++;
        }
        return new int[] {year, month, rest + 1};
    }
}
