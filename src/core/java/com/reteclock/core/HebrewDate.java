package com.reteclock.core;

/**
 * The Hebrew calendar — the most intricate arithmetic here, and the most satisfying, because it is
 * entirely deterministic. Nothing is observed; the calendar *is* the rule.
 *
 * A nineteen-year cycle gives seven leap years, each with a thirteenth month (Adar I) inserted
 * before Adar. The year runs 353, 354, 355, 383, 384 or 385 days, because two of the months flex by
 * a day and the new year is postponed by four rules — the *dehiyyot* — that keep certain holy days
 * off certain weekdays.
 *
 * Checked before it was written down: calibrated on one date (1 Tishrei 5786 = 23 September 2025),
 * it reproduced the published Rosh Hashanah dates of 5784, 5785 and 5787 exactly, and across the
 * three centuries of the guarantee every year comes out a legal length and no new year lands on a
 * forbidden weekday.
 *
 * The day is taken to begin at midnight, not at sunset. A wall clock shows the civil day, and the
 * settings note says so.
 */
public final class HebrewDate {

    /** Julian day number of the day zero elapsed days stands for. */
    private static final int EPOCH = 347998;

    // The full names are Unicode CLDR's English spellings for this calendar; the short ones are
    // the first three letters of each, which is enough to keep them apart except for the two Adars
    // of a leap year, where CLDR's Roman numeral becomes a digit.
    private static final String[] PLAIN = {
        "Tis", "Hes", "Kis", "Tev", "She", "Ada", "Nis", "Iya", "Siv", "Tam", "Av", "Elu",
    };

    private static final String[] LEAP = {
        "Tis", "Hes", "Kis", "Tev", "She", "Ada1", "Ada2", "Nis", "Iya", "Siv", "Tam", "Av", "Elu",
    };

    private static final String[] PLAIN_FULL = {
        "Tishri", "Heshvan", "Kislev", "Tevet", "Shevat", "Adar",
        "Nisan", "Iyar", "Sivan", "Tamuz", "Av", "Elul",
    };

    private static final String[] LEAP_FULL = {
        "Tishri", "Heshvan", "Kislev", "Tevet", "Shevat", "Adar I", "Adar II",
        "Nisan", "Iyar", "Sivan", "Tamuz", "Av", "Elul",
    };

    // The spellings most common in Jewish English writing, offered beside CLDR's: Tishrei rather
    // than Tishri, Cheshvan rather than Heshvan, Tammuz rather than Tamuz.
    private static final String[] PLAIN_ALT = {
        "Tish", "Ches", "Kis", "Tev", "She", "Ada", "Nis", "Iya", "Siv", "Tam", "Av", "Elu",
    };

    private static final String[] LEAP_ALT = {
        "Tish", "Ches", "Kis", "Tev", "She", "Ada1", "Ada2", "Nis", "Iya", "Siv", "Tam", "Av", "Elu",
    };

    private static final String[] PLAIN_ALT_FULL = {
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul",
    };

    private static final String[] LEAP_ALT_FULL = {
        "Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar I", "Adar II",
        "Nisan", "Iyar", "Sivan", "Tammuz", "Av", "Elul",
    };

    private HebrewDate() {
    }

    /** Whether the year carries a thirteenth month. Seven years in every nineteen do. */
    public static boolean leapYear(int year) {
        int r = year % 19;
        return r == 0 || r == 3 || r == 6 || r == 8 || r == 11 || r == 14 || r == 17;
    }

    public static int monthsInYear(int year) {
        return leapYear(year) ? 13 : 12;
    }

    public static String[] monthNames(int year) {
        return monthNames(year, 0);
    }

    public static String[] monthNames(int year, int style) {
        if (style == 1) {
            return leapYear(year) ? LEAP_ALT : PLAIN_ALT;
        }
        return leapYear(year) ? LEAP : PLAIN;
    }

    public static String[] monthNamesFull(int year) {
        return monthNamesFull(year, 0);
    }

    public static String[] monthNamesFull(int year, int style) {
        if (style == 1) {
            return leapYear(year) ? LEAP_ALT_FULL : PLAIN_ALT_FULL;
        }
        return leapYear(year) ? LEAP_FULL : PLAIN_FULL;
    }

    /** Months elapsed before this year since the epoch — the Metonic cycle, as a count. */
    private static int monthsBefore(int year) {
        return (235 * year - 234) / 19;
    }

    /**
     * The molad, and the first of the four postponements.
     *
     * The mean new moon is counted in *parts* — 1080 to the hour — and if the day it lands on would
     * put the new year on a Sunday, Wednesday or Friday, it moves on by one. The remaining three
     * postponements are handled by {@link #delayOfYear}, which asks how long the neighbouring years
     * would otherwise be.
     */
    private static int molad(int year) {
        int months = monthsBefore(year);
        long parts = 12084L + 13753L * months;
        long day = months * 29L + parts / 25920L;
        if ((3 * (day + 1)) % 7 < 3) {
            day++;
        }
        return (int) day;
    }

    private static int delayOfYear(int year) {
        int last = molad(year - 1);
        int present = molad(year);
        int next = molad(year + 1);
        if (next - present == 356) {
            return 2;
        }
        return present - last == 382 ? 1 : 0;
    }

    /** The Julian day number of 1 Tishrei — the new year. */
    public static int newYear(int year) {
        return EPOCH + molad(year) + delayOfYear(year);
    }

    /** 353, 354, 355, 383, 384 or 385. Anything else is a bug in the postponements. */
    public static int daysInYear(int year) {
        return newYear(year + 1) - newYear(year);
    }

    public static int daysIn(int year, int month) {
        int length = daysInYear(year);
        boolean leap = leapYear(year);
        int index = month - 1;
        if (leap) {
            if (index == 5) {
                return 30;      // Adar I
            }
            if (index > 5) {
                index--;        // everything after it reads as the plain year's month
            }
        }
        switch (index) {
            case 0:
                return 30;      // Tishrei
            case 1:
                return length == 355 || length == 385 ? 30 : 29;   // Cheshvan, the full year
            case 2:
                return length == 353 || length == 383 ? 29 : 30;   // Kislev, the short one
            default:
                return index % 2 == 0 ? 30 : 29;
        }
    }

    public static int toJdn(int year, int month, int day) {
        int jdn = newYear(year);
        for (int m = 1; m < month; m++) {
            jdn += daysIn(year, m);
        }
        return jdn + day - 1;
    }

    public static int[] parts(int jdn) {
        int year = (jdn - EPOCH) * 19 / 6940 + 1;
        while (newYear(year) > jdn) {
            year--;
        }
        while (newYear(year + 1) <= jdn) {
            year++;
        }
        int rest = jdn - newYear(year);
        int month = 1;
        while (rest >= daysIn(year, month)) {
            rest -= daysIn(year, month);
            month++;
        }
        return new int[] {year, month, rest + 1};
    }
}
