package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the clock face for a given screen size.
 *
 * The layout only decides which text goes where, how large it is, and how bright it is.
 * The view measures the actual glyphs with a Paint and calls {@link #shrinkToFit} when a
 * string turns out wider than its box, so this class stays free of android.* imports and
 * can be unit tested on a plain JVM.
 *
 * Wide screens (width > height):
 *
 *   +---------------------------+--------------+
 *   |                           |  25s         |
 *   |        13:45              |  Sun         |
 *   |                           |  July 10     |
 *   |                           |  2026        |
 *   +---------------------------+--------------+
 *
 * Tall screens (height >= width):
 *
 *   13            (hour)
 *   45            (minute)
 *   Sun, July 10  (weekday and date)
 *   2026   25s    (year and seconds)
 */
public final class ClockLayout {

    /** What a slot shows. The view maps the role to a string from {@link ClockText}. */
    public static final String ROLE_HOUR_MINUTE = "hour_minute";
    public static final String ROLE_HOUR = "hour";
    public static final String ROLE_MINUTE = "minute";
    public static final String ROLE_SECOND = "second";
    public static final String ROLE_WEEKDAY = "weekday";
    public static final String ROLE_MONTH_DAY = "month_day";
    public static final String ROLE_YEAR = "year";
    public static final String ROLE_WEEKDAY_DATE = "weekday_date";
    public static final String ROLE_SMALL_LINE = "small_line";

    /** One line of text: what it is, where its center sits, how big it is, how bright it is. */
    public static final class Slot {
        public final String role;
        /** Center x of the text box, in pixels. */
        public final float centerX;
        /** Center y of the text box, in pixels. */
        public final float centerY;
        /** Text size in pixels, before {@link #shrinkToFit}. */
        public final float textSize;
        /** Width the text must fit into, in pixels. */
        public final float maxWidth;
        /** Alpha 0..255. Secondary lines are dimmer than the main time. */
        public final int alpha;

        Slot(String role, float centerX, float centerY, float textSize, float maxWidth, int alpha) {
            this.role = role;
            this.centerX = centerX;
            this.centerY = centerY;
            this.textSize = textSize;
            this.maxWidth = maxWidth;
            this.alpha = alpha;
        }
    }

    private static final int ALPHA_PRIMARY = 255;
    private static final int ALPHA_SECONDARY = 190;
    private static final int ALPHA_TERTIARY = 140;

    /** Fraction of a wide screen given to the big time block. */
    private static final float WIDE_MAIN_FRACTION = 0.62f;

    /** Padding kept free on every edge, as a fraction of the shorter edge. */
    private static final float PADDING_FRACTION = 0.04f;

    private final boolean wide;
    private final List<Slot> slots;

    private ClockLayout(boolean wide, List<Slot> slots) {
        this.wide = wide;
        this.slots = slots;
    }

    /** True when the wide (landscape) arrangement is used. */
    public boolean isWide() {
        return wide;
    }

    /** The lines to draw, in drawing order. */
    public List<Slot> slots() {
        return slots;
    }

    /** Builds the layout for a screen of the given pixel size. */
    public static ClockLayout of(int widthPx, int heightPx) {
        return widthPx > heightPx ? wide(widthPx, heightPx) : tall(widthPx, heightPx);
    }

    private static ClockLayout wide(int w, int h) {
        float pad = Math.min(w, h) * PADDING_FRACTION;
        float mainWidth = w * WIDE_MAIN_FRACTION;
        float sideWidth = w - mainWidth;
        float sideCenterX = mainWidth + sideWidth / 2f;
        float sideBoxWidth = sideWidth - 2f * pad;

        List<Slot> out = new ArrayList<Slot>(5);
        out.add(new Slot(ROLE_HOUR_MINUTE, mainWidth / 2f, h / 2f,
                h * 0.60f, mainWidth - 2f * pad, ALPHA_PRIMARY));

        // Four stacked lines on the right, as a block centered vertically.
        float[] sizes = {h * 0.13f, h * 0.15f, h * 0.13f, h * 0.11f};
        String[] roles = {ROLE_SECOND, ROLE_WEEKDAY, ROLE_MONTH_DAY, ROLE_YEAR};
        int[] alphas = {ALPHA_TERTIARY, ALPHA_SECONDARY, ALPHA_SECONDARY, ALPHA_TERTIARY};
        float lineGap = h * 0.045f;

        float blockHeight = lineGap * (sizes.length - 1);
        for (int i = 0; i < sizes.length; i++) {
            blockHeight += sizes[i];
        }
        float cursor = h / 2f - blockHeight / 2f;
        for (int i = 0; i < sizes.length; i++) {
            float centerY = cursor + sizes[i] / 2f;
            out.add(new Slot(roles[i], sideCenterX, centerY, sizes[i], sideBoxWidth, alphas[i]));
            cursor += sizes[i] + lineGap;
        }
        return new ClockLayout(true, out);
    }

    private static ClockLayout tall(int w, int h) {
        float pad = Math.min(w, h) * PADDING_FRACTION;
        float boxWidth = w - 2f * pad;
        float centerX = w / 2f;

        List<Slot> out = new ArrayList<Slot>(4);
        out.add(new Slot(ROLE_HOUR, centerX, h * 0.20f, h * 0.26f, boxWidth, ALPHA_PRIMARY));
        out.add(new Slot(ROLE_MINUTE, centerX, h * 0.48f, h * 0.26f, boxWidth, ALPHA_PRIMARY));
        out.add(new Slot(ROLE_WEEKDAY_DATE, centerX, h * 0.71f, h * 0.075f, boxWidth, ALPHA_SECONDARY));
        out.add(new Slot(ROLE_SMALL_LINE, centerX, h * 0.85f, h * 0.045f, boxWidth, ALPHA_TERTIARY));
        return new ClockLayout(false, out);
    }

    /**
     * Scales a text size down so that measured text fits its box.
     *
     * @param textSize      the size the text was measured at
     * @param measuredWidth width of the text at {@code textSize}
     * @param maxWidth      width the text must fit into
     * @return {@code textSize} when it already fits, otherwise a smaller size
     */
    public static float shrinkToFit(float textSize, float measuredWidth, float maxWidth) {
        if (measuredWidth <= maxWidth || measuredWidth <= 0f || maxWidth <= 0f) {
            return textSize;
        }
        return textSize * (maxWidth / measuredWidth);
    }
}
