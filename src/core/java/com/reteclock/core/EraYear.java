package com.reteclock.core;

/**
 * The calendars that are the Gregorian one with a different year written on it.
 *
 * Same months, same lengths, same leap rule — only the year is counted from somewhere else. Each is
 * an addition, and each serves a country, which is the best value-per-line in this feature.
 *
 * Two of them have an edge inside the guaranteed span, and both are handled here rather than left
 * to surprise somebody:
 *
 * - **Minguo does not exist before 1912.** The Republic was founded that year, so 1911 has no
 *   Minguo year at all; {@link Calendars} falls back to the Gregorian date there.
 * - **Thailand moved its new year.** Until 1941 the Buddhist year turned on 1 April, so a January
 *   date in 1940 belongs to the Buddhist year *before* the one a plain +543 would give. The switch
 *   made B.E. 2483 a nine-month year, and this is the only place the app has to know it.
 *
 * And one that has an edge nobody can close: **the Japanese era changes when the reign does**, on a
 * date nobody can know in advance. The app counts Reiwa onward from 2019 and the README says that
 * is an assumption, not a computation.
 */
public final class EraYear {

    /** Buddhist Era: the Gregorian year plus 543, once the new year had moved to January. */
    public static final int THAI_OFFSET = 543;

    /** The first day the plain +543 rule holds: 1 January 1941. */
    private static final int THAI_JANUARY_NEW_YEAR = 2429996;

    /** Minguo year 1 is 1912; before that the calendar has nothing to say. */
    public static final int MINGUO_YEAR_ZERO = 1911;

    /** Where each Japanese era begins: Julian day number, then the Gregorian year it starts in. */
    private static final int[][] JAPANESE_ERAS = {
        // Meiji is already running when the span opens in 1900.
        {Integer.MIN_VALUE, 1868},
        {2419614, 1912},    // Taisho,  30 July 1912
        {2424875, 1926},    // Showa,   25 December 1926
        {2447535, 1989},    // Heisei,  8 January 1989
        {2458605, 2019},    // Reiwa,   1 May 2019
    };

    private static final String[] JAPANESE_NAMES = {"Meiji", "Taisho", "Showa", "Heisei", "Reiwa"};

    private EraYear() {
    }

    /** The Buddhist Era year of a Julian day number. */
    public static int thaiYear(int jdn) {
        int[] date = Gregorian.parts(jdn);
        if (jdn >= THAI_JANUARY_NEW_YEAR) {
            return date[0] + THAI_OFFSET;
        }
        return date[0] + (date[1] >= 4 ? THAI_OFFSET : THAI_OFFSET - 1);
    }

    /** The Gregorian year a Buddhist Era year and month belong to. */
    public static int thaiGregorianYear(int year, int month) {
        int gregorian = year - THAI_OFFSET;
        if (Gregorian.toJdn(gregorian, month, 1) >= THAI_JANUARY_NEW_YEAR) {
            return gregorian;
        }
        return month >= 4 ? gregorian : gregorian + 1;
    }

    /** Which of the five eras a day falls in, as an index. */
    public static int japaneseEra(int jdn) {
        int era = 0;
        for (int i = 1; i < JAPANESE_ERAS.length; i++) {
            if (jdn >= JAPANESE_ERAS[i][0]) {
                era = i;
            }
        }
        return era;
    }

    /** The year within the era, counting the first as year 1. */
    public static int japaneseYear(int jdn) {
        return Gregorian.year(jdn) - JAPANESE_ERAS[japaneseEra(jdn)][1] + 1;
    }

    /** "Reiwa", "Heisei", and so on. */
    public static String japaneseEraName(int jdn) {
        return JAPANESE_NAMES[japaneseEra(jdn)];
    }

    /** The Gregorian year an era and a year within it stand for. */
    public static int japaneseGregorianYear(int era, int year) {
        return JAPANESE_ERAS[era][1] + year - 1;
    }

    /** How many eras there are, for the callers that walk them. */
    public static int japaneseEraCount() {
        return JAPANESE_ERAS.length;
    }
}
