package com.reteclock.core;

/**
 * The Islamic (Hijri) calendar, by the tabular rule, with an offset the user sets.
 *
 * Twelve lunar months, alternately 30 and 29 days, and a 354-day year that gains a day in eleven
 * years of every thirty. The honest difficulty is not the arithmetic: a Hijri month begins when the
 * new crescent is *seen*, so the date genuinely differs between countries and authorities, and no
 * computation is the announcement.
 *
 * That disagreement was measured rather than guessed (RFC-0003, D17): across 503 months, this rule
 * and a conjunction-based one differ by at most two days, and by one day or none for 95% of them.
 * So the app computes with this rule and lets a user shift it by −2…+2 days once, to match what
 * their own community observes — after which every date is right for them. The offset is applied to
 * the day number before conversion, which is the only place it can be applied without making some
 * month the wrong length.
 */
public final class IslamicDate {

    /** Julian day number of 1 Muharram 1 AH by this rule. */
    private static final int EPOCH = 1948440;

    /**
     * The abbreviations Unicode CLDR gives for this calendar in English, in ASCII.
     *
     * This is the one non-Gregorian calendar in the app for which a standard *short* form exists:
     * CLDR has `Muh.` `Saf.` `Rab. I` `Rab. II` `Jum. I` `Jum. II` `Raj.` `Sha.` `Ram.` `Shaw.`
     * `Dhu'l-Q.` `Dhu'l-H.`. Dropped here: the full stops, the modifier letter in `Dhu'l`, and the
     * space; the Roman numerals become digits so nothing exceeds four characters.
     */
    public static final String[] MONTHS = {
        "Muh", "Saf", "Rab1", "Rab2", "Jum1", "Jum2", "Raj", "Sha", "Ram", "Shaw", "DhuQ", "DhuH",
    };

    /** CLDR's wide forms, in ASCII. */
    public static final String[] MONTHS_FULL = {
        "Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II",
        "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhul-Qidah", "Dhul-Hijjah",
    };

    /**
     * The same months as the Malay-speaking world writes them, from CLDR's Indonesian data.
     *
     * Not a translation and not a rival spelling of the first set — a different naming tradition,
     * used by something like a quarter of a billion people. Somebody in Kuala Lumpur reads Zulkaedah
     * where somebody in Cairo reads Dhu al-Qidah, and neither is the correction of the other.
     */
    public static final String[] MONTHS_MALAY = {
        "Muh", "Saf", "RbAw", "RbAk", "JmAw", "JmAk", "Raj", "Sya", "Ram", "Syaw", "Zulk", "Zulh",
    };

    public static final String[] MONTHS_MALAY_FULL = {
        "Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jumadilawal", "Jumadilakhir",
        "Rajab", "Syakban", "Ramadan", "Syawal", "Zulkaidah", "Zulhijah",
    };

    /**
     * And as South Asia writes them — Urdu, and the English of Pakistan, India and Bangladesh.
     *
     * The largest readership of this calendar by some way, and the one furthest from the Arabic
     * transliteration: Zilqad rather than Dhu al-Qidah, Ramzan rather than Ramadan.
     */
    public static final String[] MONTHS_URDU = {
        "Muh", "Saf", "RbAw", "RbSa", "JmAw", "JmSa", "Raj", "Sha", "Ram", "Shaw", "ZilQ", "ZilH",
    };

    public static final String[] MONTHS_URDU_FULL = {
        "Muharram", "Safar", "Rabi-ul-Awwal", "Rabi-us-Sani", "Jamadi-ul-Awwal", "Jamadi-us-Sani",
        "Rajab", "Shaban", "Ramzan", "Shawwal", "Zilqad", "Zilhaj",
    };

    /** And as Turkey writes them, from CLDR's Turkish data, with the diacritics dropped. */
    public static final String[] MONTHS_TURKISH = {
        "Muha", "Safe", "Reve", "Rahi", "Ceve", "Cahi", "Rece", "Saba", "Rama", "Sevv", "Zilk", "Zilh",
    };

    public static final String[] MONTHS_TURKISH_FULL = {
        "Muharrem", "Safer", "Rebiulevvel", "Rebiulahir", "Cemaziyelevvel", "Cemaziyelahir",
        "Recep", "Saban", "Ramazan", "Sevval", "Zilkade", "Zilhicce",
    };

    private IslamicDate() {
    }

    // ---- Umm al-Qura ------------------------------------------------------------------------
    //
    // The other reckoning: Saudi Arabia's calculated calendar, which is what most printed Hijri
    // dates follow. It is a table rather than a rule (see UmalquraTable), and it disagrees with the
    // tabular rule about which day a month begins on in more than a third of all months — so this
    // is not a spelling choice like the namings, it is a different answer to what day it is.
    //
    // Outside the years the table covers, the tabular rule answers instead. That is the honest
    // fallback: an empty screen would be worse, and a table extrapolated past its data would be a
    // guess wearing the clothes of an authority.

    public static boolean umalquraCovers(int year) {
        return UmalquraTable.covers(year);
    }

    public static int umalquraDaysIn(int year, int month) {
        return UmalquraTable.covers(year)
                ? UmalquraTable.daysInMonth(year, month) : daysIn(year, month);
    }

    public static int umalquraToJdn(int year, int month, int day) {
        if (!UmalquraTable.covers(year)) {
            return toJdn(year, month, day);
        }
        int jdn = UmalquraTable.newYear(year);
        for (int m = 1; m < month; m++) {
            jdn += UmalquraTable.daysInMonth(year, m);
        }
        return jdn + day - 1;
    }

    // ---- MABIMS ----------------------------------------------------------------------------
    //
    // The third reckoning: the calendar Malaysia, Indonesia, Brunei and Singapore keep, computed
    // from their own crescent-visibility criteria (see MabimsTable). It begins where the criteria
    // do, in 1443 AH — August 2021 — because applying a rule agreed in 2021 to 1950 would produce a
    // calendar nobody ever used.

    public static boolean mabimsCovers(int year) {
        return MabimsTable.covers(year);
    }

    public static int mabimsDaysIn(int year, int month) {
        return MabimsTable.covers(year)
                ? MabimsTable.daysInMonth(year, month) : daysIn(year, month);
    }

    public static int mabimsToJdn(int year, int month, int day) {
        if (!MabimsTable.covers(year)) {
            return toJdn(year, month, day);
        }
        int jdn = MabimsTable.newYear(year);
        for (int m = 1; m < month; m++) {
            jdn += MabimsTable.daysInMonth(year, m);
        }
        return jdn + day - 1;
    }

    public static int[] mabimsParts(int jdn) {
        if (jdn < MabimsTable.newYear(MabimsTable.FIRST_YEAR)
                || jdn >= mabimsToJdn(MabimsTable.LAST_YEAR, 12,
                        MabimsTable.daysInMonth(MabimsTable.LAST_YEAR, 12) + 1)) {
            return parts(jdn);
        }
        int year = MabimsTable.FIRST_YEAR
                + (jdn - MabimsTable.newYear(MabimsTable.FIRST_YEAR)) / 355;
        if (year < MabimsTable.FIRST_YEAR) {
            year = MabimsTable.FIRST_YEAR;
        }
        while (year > MabimsTable.FIRST_YEAR && MabimsTable.newYear(year) > jdn) {
            year--;
        }
        while (year < MabimsTable.LAST_YEAR && MabimsTable.newYear(year + 1) <= jdn) {
            year++;
        }
        int rest = jdn - MabimsTable.newYear(year);
        int month = 1;
        while (month < 12 && rest >= MabimsTable.daysInMonth(year, month)) {
            rest -= MabimsTable.daysInMonth(year, month);
            month++;
        }
        return new int[] {year, month, rest + 1};
    }

    public static int[] umalquraParts(int jdn) {
        if (jdn < UmalquraTable.newYear(UmalquraTable.FIRST_YEAR)
                || jdn >= umalquraToJdn(UmalquraTable.LAST_YEAR, 12,
                        UmalquraTable.daysInMonth(UmalquraTable.LAST_YEAR, 12) + 1)) {
            return parts(jdn);
        }
        int year = UmalquraTable.FIRST_YEAR
                + (jdn - UmalquraTable.newYear(UmalquraTable.FIRST_YEAR)) / 355;
        if (year < UmalquraTable.FIRST_YEAR) {
            year = UmalquraTable.FIRST_YEAR;
        }
        while (year > UmalquraTable.FIRST_YEAR && UmalquraTable.newYear(year) > jdn) {
            year--;
        }
        while (year < UmalquraTable.LAST_YEAR && UmalquraTable.newYear(year + 1) <= jdn) {
            year++;
        }
        int rest = jdn - UmalquraTable.newYear(year);
        int month = 1;
        while (month < 12 && rest >= UmalquraTable.daysInMonth(year, month)) {
            rest -= UmalquraTable.daysInMonth(year, month);
            month++;
        }
        return new int[] {year, month, rest + 1};
    }

    /** How many of the years before this one were long — the 30-year cycle, written as a count. */
    private static int leapsBefore(int year) {
        return (11 * (year - 1) + 3) / 30;
    }

    public static boolean leapYear(int year) {
        return leapsBefore(year + 1) - leapsBefore(year) == 1;
    }

    public static int daysIn(int year, int month) {
        if (month == 12) {
            return leapYear(year) ? 30 : 29;
        }
        return month % 2 == 1 ? 30 : 29;
    }

    public static int toJdn(int year, int month, int day) {
        return day + 29 * (month - 1) + month / 2
                + (year - 1) * 354 + leapsBefore(year) + EPOCH - 1;
    }

    public static int[] parts(int jdn) {
        int year = (30 * (jdn - EPOCH) + 10646) / 10631;
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
