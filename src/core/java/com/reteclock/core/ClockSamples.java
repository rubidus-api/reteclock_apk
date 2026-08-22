package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every string a field can ever show.
 *
 * Text sizes are worked out from the widest of these rather than from whatever the clock happens to
 * be showing, so the size does not change with the time and the layout does not slide. That only
 * works if this list really is everything: a string a field can produce and this cannot would be
 * one that gets clipped on somebody's screen and never in a test. T013 checks both directions.
 *
 * Pure Java: no android.* imports.
 */
public final class ClockSamples {

    /**
     * The digits worth trying for a number whose value is not otherwise constrained.
     *
     * A year is four digits and the clock will be shown in one particular year, but which digits a
     * font draws widest is the font's business — "1111" is narrow in most faces and "8888" wide, and
     * some faces reverse it. Trying each digit repeated covers the extremes without enumerating ten
     * thousand years.
     */
    private static final String[] DIGITS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    private ClockSamples() {
    }

    /** Everything {@code role} can show, given these options. Empty for a role with no text. */
    public static List<String> of(String role, ClockOptions options) {
        if (ClockLayout.ROLE_HOUR.equals(role)) {
            if (options.hour12) {
                // One to twelve, written the way the clock writes them — bare by default, and with
                // a leading zero where that was asked for (R83). Taking the bare form for granted
                // is what clipped the hour in issue #38: "08" is wider than "12" in most faces, so
                // a field measured against "12" is a field asked to draw something wider than
                // itself. The Japanese way of writing noon and midnight adds "0", and the 24-hour
                // midnight adds "00": both are the convention's own reading rather than a padded
                // number, so both are listed as they are written.
                boolean padded = options.padding.hour(true);
                List<String> out = new ArrayList<String>(14);
                for (int value = 1; value <= 12; value++) {
                    out.add(Padding.write(value, padded));
                }
                if (options.noonStyle == ClockOptions.NOON_ZERO
                        || options.midnightStyle == ClockOptions.MIDNIGHT_ZERO) {
                    out.add("0");
                }
                if (options.midnightStyle == ClockOptions.MIDNIGHT_24H) {
                    out.add("00");
                }
                return out;
            }
            return range(0, 23, options.padding.hour(false));
        }
        if (ClockLayout.ROLE_MINUTE.equals(role)) {
            return range(0, 59, options.padding.minute());
        }
        if (ClockLayout.ROLE_SECOND.equals(role)) {
            List<String> out = new ArrayList<String>(60);
            for (String value : range(0, 59, options.padding.second())) {
                out.add(value + "s");
            }
            return out;
        }
        if (ClockLayout.ROLE_MERIDIEM.equals(role)) {
            if (!options.hour12) {
                return Collections.<String>emptyList();
            }
            // The user's own markers as well as the built-in ones: the room kept for the marker is
            // measured from this list, and a word is wider than "AM".
            List<String> out = list(new String[] {"AM", "PM", "NN", "MN"});
            add(out, options.markers.amEntry());
            add(out, options.markers.pmEntry());
            add(out, options.markers.noonEntry());
            add(out, options.markers.midnightEntry());
            return out;
        }
        if (ClockLayout.ROLE_WEEKDAY.equals(role)) {
            return named(Calendars.weekdayNames(options.calendarSystem, options.weekdayStyle),
                    options, false);
        }
        if (ClockLayout.ROLE_MONTH_DAY.equals(role)) {
            return monthDays(options);
        }
        if (ClockLayout.ROLE_YEAR.equals(role)) {
            return years(options);
        }
        return Collections.emptyList();
    }

    /**
     * The built-in names with the user's own put in their place.
     *
     * The sizing pass asks what the widest thing a field can ever show is, and after renaming that
     * is whatever the user typed. Feeding their names in here is what makes a long one shrink the
     * line to fit rather than run off the edge of it — the settings screen still warns that a long
     * name costs size, because it does.
     */
    private static void add(List<String> out, String value) {
        if (value != null && value.length() > 0) {
            out.add(value);
        }
    }

    private static List<String> named(String[] built, ClockOptions options, boolean months) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < built.length; i++) {
            out.add(months ? options.names.month(i + 1, built[i])
                    : options.names.weekday(i, built[i]));
        }
        return out;
    }

    /**
     * The widest of these strings under the given measurer, or 0 when there are none.
     *
     * Kept here rather than in the caller because "widest sample" is the only thing anyone wants
     * from a sample list, and doing it in one place keeps the ties broken the same way everywhere.
     */
    public static float widest(List<String> samples, Widths widths) {
        float max = 0f;
        for (String sample : samples) {
            float width = widths.of(sample);
            if (width > max) {
                max = width;
            }
        }
        return max;
    }

    /** How wide one string is. The caller knows about fonts; this does not. */
    public interface Widths {
        float of(String text);
    }

    /**
     * Month and day: every pair the clock can write, in the style in force.
     *
     * This used to offer each month against a handful of extreme days — 1, 8, 28, 30, 31 — on the
     * reasoning that the widest pairing is in there somewhere. It is, for width; but a sample list
     * that does not contain `Jan 2` is a list that cannot answer "is this string covered", and the
     * check that walks a year and asks exactly that found the hole. Every day of every month is
     * 372 strings, measured once when the layout is worked out and never again, which is cheap
     * enough to be exact instead of clever.
     */
    private static List<String> monthDays(ClockOptions options) {
        List<String> out = new ArrayList<String>();
        boolean padMonth = options.padding.month();
        boolean padDay = options.padding.day(options.dateStyle);
        if (options.dateStyle != ClockOptions.DATE_STYLE_NAME) {
            // Both numeric styles: the same two numbers, told apart by their order and their
            // separator. The day-first style went unlisted until now and was measured against
            // month *names*, which are wider — wrong, though it only ever made the text small.
            boolean dayFirst = options.dateStyle == ClockOptions.DATE_STYLE_DAY_MONTH;
            String separator = dayFirst ? "/" : "-";
            for (int month = 1; month <= MONTHS_AT_MOST; month++) {
                String written = Padding.write(month, padMonth);
                for (int day = 1; day <= DAYS_AT_MOST; day++) {
                    String dayWritten = Padding.write(day, padDay);
                    out.add(dayFirst ? dayWritten + separator + written
                            : written + separator + dayWritten);
                }
            }
            return out;
        }
        for (String month : named(Calendars.monthNames(options.calendarSystem, options.nameStyle),
                options, true)) {
            for (int day = 1; day <= DAYS_AT_MOST; day++) {
                out.add(month + " " + Padding.write(day, padDay));
            }
        }
        return out;
    }

    /**
     * The widest month and day numbers any calendar here writes.
     *
     * A calendar with thirteen months names thirteen of them, and the numeric styles are written in
     * the Gregorian month numbers regardless; a day never reaches 32 anywhere. Offering a pair the
     * calendar in force cannot produce costs a measurement and nothing else — the sizing takes the
     * widest, and a string that is never drawn can only make the text smaller, never clipped.
     */
    private static final int MONTHS_AT_MOST = 13;
    private static final int DAYS_AT_MOST = 31;

    /**
     * The year, in whichever calendar is counting.
     *
     * Four digits for almost all of them. Two are different and both would be clipped by a
     * four-digit assumption: the Japanese year is an era and a number — "Reiwa 8", and "Reiwa 182"
     * by the end of the guaranteed span — and Minguo is three digits and rising.
     */
    private static List<String> years(ClockOptions options) {
        List<String> out = new ArrayList<String>();
        if (options.calendarSystem == Calendars.JAPANESE) {
            for (String era : new String[] {"Meiji", "Taisho", "Showa", "Heisei", "Reiwa"}) {
                for (String digit : DIGITS) {
                    out.add(era + " " + digit);
                    out.add(era + " " + digit + digit);
                    out.add(era + " " + digit + digit + digit);
                }
            }
            return out;
        }
        for (String digit : DIGITS) {
            out.add(digit + digit + digit + digit);
            if (options.calendarSystem == Calendars.MINGUO) {
                out.add(digit);
                out.add(digit + digit);
                out.add(digit + digit + digit);
            }
        }
        return out;
    }

    /** Every value in the range, written the way the clock in force writes it. */
    private static List<String> range(int from, int to, boolean padded) {
        List<String> out = new ArrayList<String>(to - from + 1);
        for (int value = from; value <= to; value++) {
            out.add(Padding.write(value, padded));
        }
        return out;
    }

    private static List<String> list(String[] values) {
        List<String> out = new ArrayList<String>(values.length);
        for (String value : values) {
            out.add(value);
        }
        return out;
    }
}
