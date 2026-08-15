package com.reteclock.core;

/**
 * How a calendar's months are named — which is a separate thing from how its dates are counted.
 *
 * This is the decomposition the feature grew into rather than started with. A calendar system is
 * really two independent choices:
 *
 * - **a reckoning** — the arithmetic that turns a day number into a year, a month and a day, and
 *   decides how many months the year has and how long each one is;
 * - **a naming** — what those months are called, and in whose spelling.
 *
 * They are independent because the same reckoning is read by more than one community: Iran and
 * Afghanistan share a calendar to the day and call its months different things; Cairo and the
 * Coptic liturgy do; Jakarta, Istanbul, Karachi and Cairo all keep the same Hijri months under four
 * sets of names. Before this class the two were tangled, and adding one naming meant editing four
 * switch statements — which is how you find out a model is wrong.
 *
 * So a naming is an object, the namings a reckoning has are a row in a table, and adding one is
 * adding an entry. Two of them cannot be arrays of twelve strings — the Hebrew year has twelve
 * months or thirteen, and a lunisolar year's leap month can fall anywhere — so a naming answers
 * questions rather than holding a list.
 */
public abstract class MonthNaming {

    /** The three- or four-letter name the clock draws for a month of a year. */
    public abstract String shortName(int year, int month);

    /** The same month written out, for the settings screen. */
    public abstract String fullName(int year, int month);

    /**
     * Every short name this naming can ever produce.
     *
     * Not the names of one year: text is sized from the widest string a field can show, so a Hebrew
     * leap year's Adar I has to be in here even when the year on screen has no Adar I at all.
     */
    public abstract String[] samples();

    /** A naming that is simply two lists — which is most of them. */
    public static MonthNaming of(final String[] shortNames, final String[] fullNames) {
        return new MonthNaming() {
            @Override
            public String shortName(int year, int month) {
                return shortNames[month - 1];
            }

            @Override
            public String fullName(int year, int month) {
                return fullNames[month - 1];
            }

            @Override
            public String[] samples() {
                return shortNames;
            }
        };
    }

    /** The Hebrew namings, whose year has twelve months or thirteen. */
    static MonthNaming hebrew(final int style) {
        return new MonthNaming() {
            @Override
            public String shortName(int year, int month) {
                return HebrewDate.monthNames(year, style)[month - 1];
            }

            @Override
            public String fullName(int year, int month) {
                return HebrewDate.monthNamesFull(year, style)[month - 1];
            }

            @Override
            public String[] samples() {
                String[] plain = HebrewDate.monthNames(5786, style);
                String[] leap = HebrewDate.monthNames(5787, style);
                String[] out = new String[plain.length + 2];
                System.arraycopy(leap, 0, out, 0, leap.length);
                out[out.length - 1] = plain[5];
                return out;
            }
        };
    }

    /** The lunisolar namings, which are numbers: `M6`, and `L6` for a leap month. */
    static MonthNaming lunisolar(final int locale) {
        return new MonthNaming() {
            @Override
            public String shortName(int year, int month) {
                return Lunisolar.monthName(locale, year, month);
            }

            @Override
            public String fullName(int year, int month) {
                return Lunisolar.monthNameFull(locale, year, month);
            }

            @Override
            public String[] samples() {
                String[] out = new String[24];
                for (int i = 1; i <= 12; i++) {
                    out[i - 1] = "M" + i;
                    out[i + 11] = "L" + i;
                }
                return out;
            }
        };
    }
}
