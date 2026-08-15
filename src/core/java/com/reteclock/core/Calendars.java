package com.reteclock.core;

/**
 * Every calendar the clock can count in, behind one door.
 *
 * The seam is `Gregorian day number → the calendar in force`, and it is the reason the fourteenth
 * calendar costs what the third did. Three things follow from having exactly one seam:
 *
 * - **The weekday is free.** All of these ride the same seven-day week, so the weekday of a date is
 *   `(JDN + 1) mod 7` whatever calendar is showing (see {@link CivilTime#weekday}).
 * - **Nothing assumes twelve months.** Three of these have thirteen, and in two of those the extra
 *   month is inserted in the middle rather than added at the end, so callers ask the year how many
 *   months it has (RFC-0003, D16).
 * - **Outside 1900–2200, everything answers Gregorian.** That is the guarantee this project makes,
 *   and a calendar that cannot keep it says so by handing back a Gregorian date rather than a
 *   confident wrong one (D15). {@link CalendarDate#system} reports which calendar actually answered.
 */
public final class Calendars {

    public static final int GREGORIAN = 0;
    public static final int PERSIAN = 1;
    public static final int ISLAMIC = 2;
    public static final int HEBREW = 3;
    public static final int ETHIOPIC = 4;
    public static final int COPTIC = 5;
    public static final int SAKA = 6;
    public static final int THAI = 7;
    public static final int MINGUO = 8;
    public static final int JAPANESE = 9;
    public static final int JULIAN = 10;
    public static final int CHINESE = 11;
    public static final int KOREAN = 12;
    public static final int VIETNAMESE = 13;
    /**
     * The Hijri calendar as Saudi Arabia calculates it, which is a different *reckoning* from
     * {@link #ISLAMIC} rather than a different spelling: the two disagree about the day a month
     * begins in more than a third of all months.
     */
    public static final int ISLAMIC_UMALQURA = 14;
    /**
     * And the Hijri calendar Malaysia, Indonesia, Brunei and Singapore keep, computed from the
     * crescent criteria their religious ministries agreed in 2021. A third reckoning, not a third
     * spelling: it answers a different question about what day it is.
     */
    public static final int ISLAMIC_MABIMS = 15;

    /** How many systems there are. New ones are added at the end; the numbers are stored. */
    public static final int COUNT = 16;

    /** The first day a Minguo year exists: 1 January 1912. */
    private static final int MINGUO_FIRST_JDN = 2419403;

    private static final String[] NAMES = {
        "Gregorian", "Persian", "Islamic", "Hebrew", "Ethiopian", "Coptic",
        "Indian", "Thai Buddhist", "Minguo", "Japanese", "Julian",
        "Chinese", "Korean", "Vietnamese", "Islamic Umm al-Qura", "Islamic MABIMS",
    };

    private static final String[] GREGORIAN_MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    };

    private static final String[] GREGORIAN_MONTHS_FULL = {
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    };

    private Calendars() {
    }

    /** Which lunisolar table a system reads, or -1 if it is not one of them. */
    private static int lunisolarLocale(int system) {
        switch (system) {
            case CHINESE:
                return Lunisolar.CHINA;
            case KOREAN:
                return Lunisolar.KOREA;
            case VIETNAMESE:
                return Lunisolar.VIETNAM;
            default:
                return -1;
        }
    }

    /**
     * The weekdays each calendar's own readers use, Sunday first, in three letters or fewer.
     *
     * Every calendar in the app rides the same seven-day week, so these are not different weeks —
     * they are the same week said in another language. Four of them do not name their days at all:
     * Chinese and Vietnamese number them, and Vietnam's own short forms are already Latin (`CN`,
     * `T2`…`T7`), which is what people there write on paper.
     *
     * Checked mechanically: every set is distinct at three characters. At two, four of them
     * collide — Hebrew and Indian on `Sh`, Coptic on `Pe` and `Ps`, Thai on `Ph` — so three is the
     * shortest length that works for all of them rather than a comfortable choice.
     */
    private static final String[][] NATIVE_WEEKDAYS = new String[COUNT][];

    static {
        NATIVE_WEEKDAYS[PERSIAN] =
            new String[] {"Yek", "Dos", "Ses", "Cha", "Pan", "Jom", "Sha"};
        NATIVE_WEEKDAYS[ISLAMIC] =
            new String[] {"Aha", "Ith", "Thu", "Arb", "Kha", "Jum", "Sab"};
        NATIVE_WEEKDAYS[HEBREW] =
            new String[] {"Ris", "She", "Shl", "Rev", "Cha", "Shi", "Sha"};
        NATIVE_WEEKDAYS[ETHIOPIC] =
            new String[] {"Ehu", "Seg", "Mak", "Rob", "Ham", "Arb", "Kid"};
        NATIVE_WEEKDAYS[COPTIC] =
            new String[] {"Tky", "Pes", "Psh", "Pef", "Pti", "Pso", "Psa"};
        NATIVE_WEEKDAYS[SAKA] =
            new String[] {"Rav", "Som", "Man", "Bud", "Gur", "Shu", "Sha"};
        NATIVE_WEEKDAYS[THAI] =
            new String[] {"Ath", "Cha", "Ang", "Phu", "Phr", "Suk", "Sao"};
        NATIVE_WEEKDAYS[JAPANESE] =
            new String[] {"Nic", "Get", "Ka", "Sui", "Mok", "Kin", "Do"};
        NATIVE_WEEKDAYS[KOREAN] =
            new String[] {"Il", "Wol", "Hwa", "Su", "Mok", "Geu", "To"};
        NATIVE_WEEKDAYS[CHINESE] =
            new String[] {"Ri", "Yi", "Er", "San", "Si", "Wu", "Liu"};
        NATIVE_WEEKDAYS[MINGUO] = NATIVE_WEEKDAYS[CHINESE];
        NATIVE_WEEKDAYS[VIETNAMESE] =
            new String[] {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
    }

    /** English weekdays, which every calendar can show and most people can read. */
    private static final String[] ENGLISH_WEEKDAYS = {
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
    };

    /** Two, where the calendar's readers have their own weekday names; otherwise one. */
    public static int weekdayStyleCount(int system) {
        return system >= 0 && system < COUNT && NATIVE_WEEKDAYS[system] != null ? 2 : 1;
    }

    /** The weekday names to draw: English, or the calendar's own. Sunday first, always. */
    public static String[] weekdayNames(int system, int style) {
        if (style > 0 && system >= 0 && system < COUNT && NATIVE_WEEKDAYS[system] != null) {
            return NATIVE_WEEKDAYS[system];
        }
        return ENGLISH_WEEKDAYS;
    }

    /**
     * The calendars in the order they are offered: alphabetically, by their English names.
     *
     * Not the order of the constants. Those are numbers written into a preference file and can
     * never be rearranged, but a list of fourteen has to be findable, and the only ordering that is
     * neither a ranking nor an accident of the order they were written in is the alphabet.
     */
    public static int[] byName() {
        int[] order = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            order[i] = i;
        }
        for (int i = 1; i < COUNT; i++) {
            int system = order[i];
            int j = i - 1;
            while (j >= 0 && NAMES[order[j]].compareTo(NAMES[system]) > 0) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = system;
        }
        return order;
    }

    /** The name shown in the settings. */
    public static String name(int system) {
        return system >= 0 && system < NAMES.length ? NAMES[system] : NAMES[GREGORIAN];
    }

    /** Whether this calendar can answer for this day at all, or has to defer to the Gregorian one. */
    public static boolean answers(int system, int jdn) {
        if (!CivilTime.inSpan(jdn)) {
            return false;
        }
        if (system == MINGUO) {
            return jdn >= MINGUO_FIRST_JDN;
        }
        if (system == ISLAMIC_MABIMS) {
            // The criteria are from 2021; before them this calendar did not exist in this form.
            return IslamicDate.mabimsCovers(IslamicDate.mabimsParts(jdn)[0])
                    && jdn >= IslamicDate.mabimsToJdn(MabimsTable.FIRST_YEAR, 1, 1);
        }
        if (system == ISLAMIC_UMALQURA) {
            // The Umm al-Qura calendar is a published table, and it stops at 1600 AH — late in 2174.
            // Past that it has nothing to say, so it says nothing and the Gregorian date answers
            // (D15). Continuing with the tabular rule instead would look tidier and would be a lie:
            // the two disagree by a day at the seam, so the dates would jump.
            return IslamicDate.umalquraCovers(IslamicDate.umalquraParts(jdn)[0])
                    && jdn < IslamicDate.umalquraToJdn(1601, 1, 1);
        }
        int locale = lunisolarLocale(system);
        return locale < 0 || Lunisolar.covers(locale, jdn);
    }

    /** The date a day number stands for, in the calendar asked for or the Gregorian one. */
    public static CalendarDate dateOf(int system, int jdn) {
        if (!answers(system, jdn)) {
            system = GREGORIAN;
        }
        int[] p;
        switch (system) {
            case PERSIAN:
                p = PersianDate.parts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            case ISLAMIC:
                p = IslamicDate.parts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            case ISLAMIC_UMALQURA:
                p = IslamicDate.umalquraParts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            case ISLAMIC_MABIMS:
                p = IslamicDate.mabimsParts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            case HEBREW:
                p = HebrewDate.parts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2],
                        HebrewDate.monthsInYear(p[0]),
                        HebrewDate.leapYear(p[0]) && p[1] == 6);
            case ETHIOPIC:
                p = EthiopicDate.parts(EthiopicDate.ETHIOPIC_EPOCH, jdn);
                return new CalendarDate(system, p[0], p[1], p[2], 13, p[1] == 13);
            case COPTIC:
                p = EthiopicDate.parts(EthiopicDate.COPTIC_EPOCH, jdn);
                return new CalendarDate(system, p[0], p[1], p[2], 13, p[1] == 13);
            case SAKA:
                p = SakaDate.parts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            case JULIAN:
                p = SakaDate.julianParts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            case CHINESE:
            case KOREAN:
            case VIETNAMESE: {
                int locale = lunisolarLocale(system);
                p = Lunisolar.parts(locale, jdn);
                return new CalendarDate(system, p[0], p[1], p[2],
                        Lunisolar.monthsInYear(locale, p[0]),
                        Lunisolar.isLeapMonth(locale, p[0], p[1]));
            }
            case THAI:
                p = Gregorian.parts(jdn);
                return new CalendarDate(system, EraYear.thaiYear(jdn), p[1], p[2]);
            case MINGUO:
                p = Gregorian.parts(jdn);
                return new CalendarDate(system, p[0] - EraYear.MINGUO_YEAR_ZERO, p[1], p[2]);
            case JAPANESE:
                // The year here is the *Gregorian* one, and the era is put on in yearText.
                // An era year cannot be the year a date carries: Showa 36, Heisei 36 and Reiwa 36
                // all exist, so nothing — not even "how long is February" — can be answered from
                // one. The Japanese calendar really is the Gregorian calendar with another year
                // written on it, and this is that sentence in code.
                p = Gregorian.parts(jdn);
                return new CalendarDate(system, p[0], p[1], p[2]);
            default:
                p = Gregorian.parts(jdn);
                return new CalendarDate(GREGORIAN, p[0], p[1], p[2]);
        }
    }

    /** The day number a date stands for. The inverse of {@link #dateOf}. */
    public static int jdnOf(int system, int year, int month, int day) {
        switch (system) {
            case PERSIAN:
                return PersianDate.toJdn(year, month, day);
            case ISLAMIC:
                return IslamicDate.toJdn(year, month, day);
            case ISLAMIC_UMALQURA:
                return IslamicDate.umalquraToJdn(year, month, day);
            case ISLAMIC_MABIMS:
                return IslamicDate.mabimsToJdn(year, month, day);
            case HEBREW:
                return HebrewDate.toJdn(year, month, day);
            case ETHIOPIC:
                return EthiopicDate.toJdn(EthiopicDate.ETHIOPIC_EPOCH, year, month, day);
            case COPTIC:
                return EthiopicDate.toJdn(EthiopicDate.COPTIC_EPOCH, year, month, day);
            case SAKA:
                return SakaDate.toJdn(year, month, day);
            case JULIAN:
                return SakaDate.julianToJdn(year, month, day);
            case CHINESE:
            case KOREAN:
            case VIETNAMESE:
                return Lunisolar.toJdn(lunisolarLocale(system), year, month, day);
            case THAI:
                return Gregorian.toJdn(EraYear.thaiGregorianYear(year, month), month, day);
            case MINGUO:
                return Gregorian.toJdn(year + EraYear.MINGUO_YEAR_ZERO, month, day);
            case JAPANESE:
                // As in dateOf: the year is the Gregorian one, because an era year is ambiguous.
                return Gregorian.toJdn(year, month, day);
            default:
                return Gregorian.toJdn(year, month, day);
        }
    }

    /** Twelve, or thirteen. */
    public static int monthsInYear(int system, int year) {
        switch (system) {
            case HEBREW:
                return HebrewDate.monthsInYear(year);
            case ETHIOPIC:
            case COPTIC:
                return 13;
            case CHINESE:
            case KOREAN:
            case VIETNAMESE:
                return Lunisolar.monthsInYear(lunisolarLocale(system), year);
            default:
                return 12;
        }
    }

    /** How long a month is, in the calendar and year it belongs to. */
    public static int daysInMonth(int system, int year, int month) {
        switch (system) {
            case PERSIAN:
                return PersianDate.daysIn(year, month);
            case ISLAMIC:
                return IslamicDate.daysIn(year, month);
            case ISLAMIC_UMALQURA:
                return IslamicDate.umalquraDaysIn(year, month);
            case ISLAMIC_MABIMS:
                return IslamicDate.mabimsDaysIn(year, month);
            case HEBREW:
                return HebrewDate.daysIn(year, month);
            case ETHIOPIC:
            case COPTIC:
                return EthiopicDate.daysIn(year, month);
            case SAKA:
                return SakaDate.daysIn(year, month);
            case JULIAN:
                return SakaDate.julianDaysIn(year, month);
            case THAI:
                return Gregorian.daysIn(EraYear.thaiGregorianYear(year, month), month);
            case MINGUO:
                return Gregorian.daysIn(year + EraYear.MINGUO_YEAR_ZERO, month);
            case CHINESE:
            case KOREAN:
            case VIETNAMESE:
                return Lunisolar.daysInMonth(lunisolarLocale(system), year, month);
            default:
                return Gregorian.daysIn(year, month);
        }
    }

    /**
     * Every naming each reckoning has, which is the table that used to be six switch statements.
     *
     * A row per calendar; an entry per community that reads it. Adding a naming is adding an entry
     * — no other file changes, and nothing can be added to one method and forgotten in another,
     * which is exactly what happened before this was a table.
     */
    private static final MonthNaming[][] NAMINGS = new MonthNaming[COUNT][];

    static {
        MonthNaming gregorian = MonthNaming.of(GREGORIAN_MONTHS, GREGORIAN_MONTHS_FULL);
        NAMINGS[GREGORIAN] = new MonthNaming[] {gregorian};
        NAMINGS[JULIAN] = new MonthNaming[] {gregorian};
        NAMINGS[THAI] = new MonthNaming[] {gregorian};
        NAMINGS[MINGUO] = new MonthNaming[] {gregorian};
        NAMINGS[JAPANESE] = new MonthNaming[] {gregorian};
        NAMINGS[PERSIAN] = new MonthNaming[] {
            MonthNaming.of(PersianDate.MONTHS, PersianDate.MONTHS_FULL),
            MonthNaming.of(PersianDate.MONTHS_AFGHAN, PersianDate.MONTHS_AFGHAN_FULL),
        };
        NAMINGS[ISLAMIC] = new MonthNaming[] {
            MonthNaming.of(IslamicDate.MONTHS, IslamicDate.MONTHS_FULL),
            MonthNaming.of(IslamicDate.MONTHS_MALAY, IslamicDate.MONTHS_MALAY_FULL),
            MonthNaming.of(IslamicDate.MONTHS_TURKISH, IslamicDate.MONTHS_TURKISH_FULL),
            MonthNaming.of(IslamicDate.MONTHS_URDU, IslamicDate.MONTHS_URDU_FULL),
        };
        NAMINGS[ISLAMIC_UMALQURA] = NAMINGS[ISLAMIC];
        NAMINGS[ISLAMIC_MABIMS] = NAMINGS[ISLAMIC];
        NAMINGS[HEBREW] = new MonthNaming[] {MonthNaming.hebrew(0), MonthNaming.hebrew(1)};
        NAMINGS[ETHIOPIC] = new MonthNaming[] {
            MonthNaming.of(EthiopicDate.ETHIOPIC_MONTHS, EthiopicDate.ETHIOPIC_MONTHS_FULL),
            MonthNaming.of(EthiopicDate.ETHIOPIC_MONTHS_POPULAR,
                    EthiopicDate.ETHIOPIC_MONTHS_POPULAR_FULL),
        };
        NAMINGS[COPTIC] = new MonthNaming[] {
            MonthNaming.of(EthiopicDate.COPTIC_MONTHS, EthiopicDate.COPTIC_MONTHS_FULL),
            MonthNaming.of(EthiopicDate.COPTIC_MONTHS_GREEK,
                    EthiopicDate.COPTIC_MONTHS_GREEK_FULL),
        };
        NAMINGS[SAKA] = new MonthNaming[] {
            MonthNaming.of(SakaDate.MONTHS, SakaDate.MONTHS_FULL),
            MonthNaming.of(SakaDate.MONTHS_HINDI, SakaDate.MONTHS_HINDI_FULL),
        };
        NAMINGS[CHINESE] = new MonthNaming[] {MonthNaming.lunisolar(Lunisolar.CHINA)};
        NAMINGS[KOREAN] = new MonthNaming[] {MonthNaming.lunisolar(Lunisolar.KOREA)};
        NAMINGS[VIETNAMESE] = new MonthNaming[] {MonthNaming.lunisolar(Lunisolar.VIETNAM)};
    }

    /** How many namings this calendar has: one, or one per community that reads it. */
    public static int styleCount(int system) {
        return system >= 0 && system < COUNT ? NAMINGS[system].length : 1;
    }

    /** One of them, clamped, so a stored number from a later version cannot break a clock. */
    private static MonthNaming naming(int system, int style) {
        MonthNaming[] row = NAMINGS[system >= 0 && system < COUNT ? system : GREGORIAN];
        return row[style > 0 && style < row.length ? style : 0];
    }

    /** The month name the clock draws, in the naming the user picked. */
    public static String monthName(int system, int year, int month, int style) {
        return naming(system, style).shortName(year, month);
    }

    /** And in full. */
    public static String monthNameFull(int system, int year, int month, int style) {
        return naming(system, style).fullName(year, month);
    }

    /** The month name in this calendar's first naming. */
    public static String monthName(int system, int year, int month) {
        return monthName(system, year, month, 0);
    }

    /** And in full. */
    public static String monthNameFull(int system, int year, int month) {
        return monthNameFull(system, year, month, 0);
    }

    /**
     * Every month name a naming can ever show, which is what the text is sized from.
     *
     * A name that can be drawn and is not in here is a name that gets clipped on somebody's phone
     * and in nobody's test.
     */
    public static String[] monthNames(int system, int style) {
        String[] own = naming(system, style).samples();
        if (system != ISLAMIC_UMALQURA && system != ISLAMIC_MABIMS) {
            return own;
        }
        // This one runs out of table inside the guaranteed span — its data stops in 2174 and the
        // Gregorian date answers for the rest — so the Gregorian names are among the strings it can
        // draw, and text sized without them would be text that gets clipped in 2175.
        String[] out = new String[own.length + GREGORIAN_MONTHS.length];
        System.arraycopy(own, 0, out, 0, own.length);
        System.arraycopy(GREGORIAN_MONTHS, 0, out, own.length, GREGORIAN_MONTHS.length);
        return out;
    }

    public static String[] monthNames(int system) {
        return monthNames(system, 0);
    }


    /**
     * The year as it is written.
     *
     * A number for every calendar but the Japanese one, whose year belongs to an era and reads
     * "Reiwa 8". The day number is needed because the era changes mid-year.
     */
    public static String yearText(int system, int jdn) {
        CalendarDate date = dateOf(system, jdn);
        if (date.system == JAPANESE) {
            // The date carries the Gregorian year; the era year is worked out only to be written.
            return EraYear.japaneseEraName(jdn) + " " + EraYear.japaneseYear(jdn);
        }
        return Integer.toString(date.year);
    }
}
