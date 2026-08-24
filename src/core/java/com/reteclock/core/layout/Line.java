package com.reteclock.core.layout;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A line made of more than one field (RFC-0005, R90).
 *
 * Most boxes hold one field. Two of the app's own lines hold two — the weekday with the date, and
 * the year with the seconds — and the time on one line is the hour and the minute with a colon
 * between them. A box that could not say so could not express the arrangement the app already
 * draws, which is the thing the engine has to be able to say before it may replace it.
 *
 * The separator is read in two halves, exactly as {@link ClockLayout} reads it: the visible
 * characters belong to the field before them, because a comma is punctuation attached to what it
 * follows, and the whitespace belongs to nobody so that no field's decoration can reach it.
 *
 * The composition is stated here rather than taken from `ClockLayout` on purpose. Two
 * implementations that cannot disagree cannot be compared, and T074 exists to compare them.
 */
public final class Line {

    /** One field of a line, and what comes before it. */
    public static final class Part {
        public final String field;
        public final String separatorBefore;

        Part(String field, String separatorBefore) {
            this.field = field;
            this.separatorBefore = separatorBefore;
        }
    }

    private Line() {
    }

    /**
     * What this field is made of: itself, or the fields the app's own line puts together.
     *
     * The orientation is asked for because one line differs by it. Standing up, the small line is
     * the year with the seconds; lying down it is the date line of issue #42 — the weekday, the
     * date, the year and the seconds, in the order the user set. Same name, different line, and a
     * caller that did not say which way up the phone was would get the wrong one half the time.
     */
    public static List<Part> of(String field, ClockOptions options, boolean wide) {
        List<Part> out = new ArrayList<Part>(4);
        if (ClockLayout.ROLE_HOUR_MINUTE.equals(field)) {
            out.add(new Part(ClockLayout.ROLE_HOUR, ""));
            out.add(new Part(ClockLayout.ROLE_MINUTE, ":"));
        } else if (ClockLayout.ROLE_WEEKDAY_DATE.equals(field)) {
            out.add(new Part(ClockLayout.ROLE_WEEKDAY, ""));
            out.add(new Part(ClockLayout.ROLE_MONTH_DAY, ", "));
        } else if (ClockLayout.ROLE_SMALL_LINE.equals(field)) {
            if (wide) {
                java.util.List<String> shown = options == null
                        ? java.util.Collections.<String>emptyList()
                        : options.dateOrder.shown(options.showSeconds);
                for (int i = 0; i < shown.size(); i++) {
                    out.add(new Part(shown.get(i), i == 0 ? "" : "   "));
                }
            } else {
                out.add(new Part(ClockLayout.ROLE_YEAR, ""));
                if (options != null && options.showSeconds) {
                    out.add(new Part(ClockLayout.ROLE_SECOND, "   "));
                }
            }
        } else {
            out.add(new Part(field, ""));
        }
        return Collections.unmodifiableList(out);
    }

    /** Whether this field is a line of several rather than one on its own. */
    public static boolean isComposite(String field, ClockOptions options, boolean wide) {
        return of(field, options, wide).size() > 1;
    }

    /**
     * How wide this line can ever be at this size: every field at its worst case, and the
     * separators between them.
     *
     * The visible part of a separator is measured with the field it follows and the whitespace on
     * its own, which is the same split the old layout measures — the two have to arrive at the same
     * number or the comparison between them means nothing.
     */
    public static float widest(String field, ClockOptions options, ClockLayout.Metrics metrics,
            float textSize, boolean wide) {
        List<Part> parts = of(field, options, wide);
        float total = 0f;
        for (int i = 0; i < parts.size(); i++) {
            String separator = i + 1 < parts.size() ? parts.get(i + 1).separatorBefore : "";
            String visible = ClockLayout.visibleOf(separator);
            total += widestPart(parts.get(i).field, visible, options, metrics, textSize);
            String space = ClockLayout.gapOf(separator);
            if (!space.isEmpty()) {
                total += metrics.width(ClockLayout.ROLE_GAP, space, textSize);
            }
        }
        return total;
    }

    /** One field's widest reading, with any punctuation that follows it. */
    private static float widestPart(final String field, final String suffix, ClockOptions options,
            final ClockLayout.Metrics metrics, final float textSize) {
        return com.reteclock.core.ClockSamples.widest(
                com.reteclock.core.ClockSamples.of(field, options),
                new com.reteclock.core.ClockSamples.Widths() {
                    @Override
                    public float of(String text) {
                        return metrics.width(field, text + suffix, textSize);
                    }
                });
    }
}
