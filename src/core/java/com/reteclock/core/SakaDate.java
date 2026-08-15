package com.reteclock.core;

/**
 * The Indian national calendar (Saka), and the Julian calendar, which have nothing in common except
 * that both are a rule tied to the Gregorian one and neither needs a class of its own.
 *
 * **Saka** is India's official civil calendar — the Gazette and All India Radio use it. Chaitra has
 * 30 days, 31 when the Gregorian year it begins in is a leap year; then five months of 31 and six
 * of 30. The year is Gregorian − 78 and begins on 22 March, or the 21st in a leap year. Its leap
 * rule is therefore the Gregorian one, which makes it the cheapest real calendar in the app.
 * Checked: 15 August 2026 comes out 24 Shravana 1948, which is the form the Gazette itself uses.
 *
 * **Julian** is still the liturgical calendar of several Orthodox churches. It is thirteen days
 * behind the Gregorian one now, and **fourteen from 1 March 2100** — inside the span this project
 * guarantees, which is why nothing here hard-codes thirteen.
 */
public final class SakaDate {

    // Unicode CLDR's English spellings. They separate at three letters where the popular ones did
    // not: CLDR writes Asadha and Asvina, so `Asa` and `Asv` tell them apart with no hand-picking.
    public static final String[] MONTHS = {
        "Cha", "Vai", "Jya", "Asa", "Sra", "Bha", "Asv", "Kar", "Agr", "Pau", "Mag", "Pha",
    };

    public static final String[] MONTHS_FULL = {
        "Chaitra", "Vaisakha", "Jyaistha", "Asadha", "Sravana", "Bhadra",
        "Asvina", "Kartika", "Agrahayana", "Pausa", "Magha", "Phalguna",
    };

    /**
     * The everyday Hindi spellings, offered beside CLDR's Sanskritic ones.
     *
     * India's own English-language press writes Ashwin and Kartik where CLDR writes Asvina and
     * Kartika. Both are current; neither is a mistake.
     */
    public static final String[] MONTHS_HINDI = {
        "Cha", "Vai", "Jye", "Ash", "Shra", "Bha", "Ashw", "Kar", "Agr", "Pau", "Mag", "Pha",
    };

    public static final String[] MONTHS_HINDI_FULL = {
        "Chaitra", "Vaishakh", "Jyeshth", "Ashadh", "Shravan", "Bhadrapad",
        "Ashwin", "Kartik", "Agrahayan", "Paush", "Magh", "Phalgun",
    };

    public static final String[] JULIAN_MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    };

    private SakaDate() {
    }

    /** A Saka year is long when the Gregorian year it opens in is. */
    public static boolean leapYear(int year) {
        return Gregorian.leapYear(year + 78);
    }

    public static int daysIn(int year, int month) {
        if (month == 1) {
            return leapYear(year) ? 31 : 30;
        }
        return month <= 6 ? 31 : 30;
    }

    /** The Julian day number of 1 Chaitra. */
    public static int newYear(int year) {
        int gregorian = year + 78;
        return Gregorian.toJdn(gregorian, 3, Gregorian.leapYear(gregorian) ? 21 : 22);
    }

    public static int toJdn(int year, int month, int day) {
        int jdn = newYear(year);
        for (int m = 1; m < month; m++) {
            jdn += daysIn(year, m);
        }
        return jdn + day - 1;
    }

    public static int[] parts(int jdn) {
        int year = Gregorian.year(jdn) - 78;
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

    // ---- Julian ----------------------------------------------------------------------------

    public static boolean julianLeapYear(int year) {
        return year % 4 == 0;
    }

    public static int julianDaysIn(int year, int month) {
        if (month == 2) {
            return julianLeapYear(year) ? 29 : 28;
        }
        return month == 4 || month == 6 || month == 9 || month == 11 ? 30 : 31;
    }

    public static int julianToJdn(int year, int month, int day) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
    }

    public static int[] julianParts(int jdn) {
        int c = jdn + 32082;
        int d = (4 * c + 3) / 1461;
        int e = c - 1461 * d / 4;
        int m = (5 * e + 2) / 153;
        return new int[] {
            d - 4800 + m / 10,
            m + 3 - 12 * (m / 10),
            e - (153 * m + 2) / 5 + 1,
        };
    }
}
