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
            return twoDigitRange(0, 23);
        }
        if (ClockLayout.ROLE_MINUTE.equals(role)) {
            return twoDigitRange(0, 59);
        }
        if (ClockLayout.ROLE_SECOND.equals(role)) {
            List<String> out = new ArrayList<String>(60);
            for (String value : twoDigitRange(0, 59)) {
                out.add(value + "s");
            }
            return out;
        }
        if (ClockLayout.ROLE_WEEKDAY.equals(role)) {
            return list(ClockText.WEEKDAYS);
        }
        if (ClockLayout.ROLE_MONTH_DAY.equals(role)) {
            return monthDays(options);
        }
        if (ClockLayout.ROLE_YEAR.equals(role)) {
            List<String> out = new ArrayList<String>(DIGITS.length);
            for (String digit : DIGITS) {
                out.add(digit + digit + digit + digit);
            }
            return out;
        }
        return Collections.emptyList();
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
     * Month and day.
     *
     * Under the named style this is a month name and a day number, so the widest is the widest name
     * beside the widest number — but which pair that is depends on the font, and measuring all 366
     * combinations to find out would be wasteful. Every month is offered with the two- and
     * one-digit day extremes, which is 36 strings and covers the pairing that actually wins.
     *
     * The numeric style is a fixed shape, so the digits are what vary.
     */
    private static List<String> monthDays(ClockOptions options) {
        List<String> out = new ArrayList<String>();
        if (options.dateStyle == ClockOptions.DATE_STYLE_NUMERIC) {
            for (String digit : DIGITS) {
                out.add(digit + digit + "-" + digit + digit);
            }
            // The real range never has a month above 12 or a day above 31, but a font can draw "1"
            // wider than "9"; these are the shapes that actually occur at the extremes.
            out.add("12-28");
            out.add("12-30");
            out.add("12-31");
            out.add("11-11");
            return out;
        }
        for (String month : ClockText.MONTHS) {
            out.add(month + " 1");
            out.add(month + " 8");
            out.add(month + " 28");
            out.add(month + " 30");
            out.add(month + " 31");
        }
        return out;
    }

    private static List<String> twoDigitRange(int from, int to) {
        List<String> out = new ArrayList<String>(to - from + 1);
        for (int value = from; value <= to; value++) {
            out.add(value < 10 ? "0" + value : String.valueOf(value));
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
