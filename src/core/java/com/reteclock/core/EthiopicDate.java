package com.reteclock.core;

/**
 * The Ethiopian calendar, and the Coptic one, which is the same calendar with another epoch.
 *
 * Twelve months of thirty days and a thirteenth of five — six in a leap year, which is every fourth
 * with no century exception. The new year falls on 11 September, or the 12th in the year before a
 * Gregorian leap year. Ethiopia runs seven to eight years behind: today is 2018 there.
 *
 * The arithmetic looks trivial and one part of it is not. The textbook closed-form inverse,
 * {@code (4n+3)/1461}, misfiles the days of the thirteenth month in leap years — it round-tripped
 * 13,908 days *wrong* out of 55,152 before a settle step was added. A calendar that looks simple is
 * exactly where a wrong inverse ships unnoticed, so this one estimates and then settles, like all
 * the others here.
 */
public final class EthiopicDate {

    /** Julian day number of 1 Meskerem 1 in the Ethiopian reckoning. */
    public static final int ETHIOPIC_EPOCH = 1724221;

    /** And of 1 Thout 1 in the Coptic one. */
    public static final int COPTIC_EPOCH = 1825030;

    // Both name sets are Unicode CLDR's English spellings, and the short forms are the shortest
    // prefix that keeps that calendar's months apart — three letters for Ethiopic, four for Coptic.
    //
    // Coptic needs one exception, and it is the only one in the app. CLDR uses the Egyptian names,
    // where Baramhat and Baramouda agree for five letters: no prefix separates them inside four.
    // So those two drop their interior vowels instead — Brmh and Brmd — which is the same trick
    // every abbreviation reaches for when a prefix will not do, and it keeps the whole app inside
    // four characters.
    public static final String[] ETHIOPIC_MONTHS = {
        "Mes", "Tek", "Hed", "Tah", "Ter", "Yek", "Meg", "Mia", "Gen", "Sen", "Ham", "Neh", "Pag",
    };

    public static final String[] ETHIOPIC_MONTHS_FULL = {
        "Meskerem", "Tekemt", "Hedar", "Tahsas", "Ter", "Yekatit", "Megabit",
        "Miazia", "Genbot", "Sene", "Hamle", "Nehasse", "Pagumen",
    };

    public static final String[] COPTIC_MONTHS = {
        "Tout", "Baba", "Hato", "Kiah", "Toba", "Amsh", "Brmh",
        "Brmd", "Bash", "Paon", "Epep", "Mesr", "Nasi",
    };

    public static final String[] COPTIC_MONTHS_FULL = {
        "Tout", "Baba", "Hator", "Kiahk", "Toba", "Amshir", "Baramhat",
        "Baramouda", "Bashans", "Paona", "Epep", "Mesra", "Nasie",
    };

    /**
     * The Coptic names, which the liturgy and much of the diaspora use.
     *
     * The same thirteen months of the same calendar; Egypt says Baramhat and the church books say
     * Paremhat. Offered beside the other set rather than instead of it, because choosing one would
     * be choosing between two communities who both read this calendar. These need no exception to
     * fit four characters — every one is a plain prefix.
     */
    public static final String[] COPTIC_MONTHS_GREEK = {
        "Thou", "Paop", "Hath", "Koia", "Tobi", "Mesh", "Pare",
        "Parm", "Pash", "Paon", "Epip", "Mesr", "Nasi",
    };

    public static final String[] COPTIC_MONTHS_GREEK_FULL = {
        "Thout", "Paopi", "Hathor", "Koiak", "Tobi", "Meshir", "Paremhat",
        "Parmouti", "Pashons", "Paoni", "Epip", "Mesra", "Nasie",
    };

    /** The Ethiopian names as English-language Ethiopian papers usually spell them. */
    public static final String[] ETHIOPIC_MONTHS_POPULAR = {
        "Mes", "Tik", "Hid", "Tah", "Tir", "Yek", "Meg", "Miy", "Gin", "Sen", "Ham", "Neh", "Pag",
    };

    public static final String[] ETHIOPIC_MONTHS_POPULAR_FULL = {
        "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit", "Megabit",
        "Miyazia", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume",
    };

    private EthiopicDate() {
    }

    /** Every fourth year, with no century exception — the third of each cycle. */
    public static boolean leapYear(int year) {
        return year % 4 == 3;
    }

    public static int monthsInYear() {
        return 13;
    }

    public static int daysIn(int year, int month) {
        if (month < 13) {
            return 30;
        }
        return leapYear(year) ? 6 : 5;
    }

    public static int toJdn(int epoch, int year, int month, int day) {
        return epoch - 1 + 365 * (year - 1) + year / 4 + 30 * (month - 1) + day;
    }

    public static int[] parts(int epoch, int jdn) {
        int year = (4 * (jdn - epoch + 1) + 1463) / 1461;
        while (toJdn(epoch, year, 1, 1) > jdn) {
            year--;
        }
        while (toJdn(epoch, year + 1, 1, 1) <= jdn) {
            year++;
        }
        int rest = jdn - toJdn(epoch, year, 1, 1);
        return new int[] {year, rest / 30 + 1, rest % 30 + 1};
    }
}
