package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the clock face for a given screen size and set of options.
 *
 * The hour and the minute get every pixel the secondary lines do not need: the layout first
 * reserves space for the smaller lines and the gaps, then hands the rest to the big time. The view
 * measures the actual glyphs with a Paint and calls {@link #shrinkToFit} when a string is wider than
 * its box, so a long string is scaled down instead of being clipped. This class stays free of
 * android.* imports and is unit tested on a plain JVM.
 *
 * Wide screens (width > height):
 *
 *   +---------------------------+--------------+
 *   |                           |  25s         |
 *   |        13:45              |  Sun         |
 *   |         (bold)            |  Jul 12      |
 *   |                           |  2026        |
 *   +---------------------------+--------------+
 *
 * Tall screens (height >= width):
 *
 *   13            (hour, bold)
 *   45            (minute, bold)
 *   Sun, Jul 12
 *   2026   25s
 *
 * The seconds line disappears when the user turns the seconds off; the freed space goes to the
 * hour and the minute.
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

    /** One line of text: what it is, where its center sits, how big it is, whether it is bold. */
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
        /** True for the hour and the minute, which are drawn bold. */
        public final boolean bold;

        Slot(String role, float centerX, float centerY, float textSize, float maxWidth, boolean bold) {
            this.role = role;
            this.centerX = centerX;
            this.centerY = centerY;
            this.textSize = textSize;
            this.maxWidth = maxWidth;
            this.bold = bold;
        }
    }

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
    public static ClockLayout of(int widthPx, int heightPx, ClockOptions options) {
        return widthPx > heightPx
                ? wide(widthPx, heightPx, options)
                : tall(widthPx, heightPx, options);
    }

    private static ClockLayout wide(int w, int h, ClockOptions options) {
        float pad = Math.min(w, h) * PADDING_FRACTION;
        float mainWidth = w * WIDE_MAIN_FRACTION;
        float sideWidth = w - mainWidth;
        float sideCenterX = mainWidth + sideWidth / 2f;
        float sideBoxWidth = sideWidth - 2f * pad;

        List<Slot> out = new ArrayList<Slot>(5);

        // The big time takes the full height between the paddings; the view scales it down only if
        // it would be wider than its half of the screen.
        out.add(new Slot(ROLE_HOUR_MINUTE, mainWidth / 2f, h / 2f,
                h - 2f * pad, mainWidth - 2f * pad, true));

        List<String> roles = new ArrayList<String>(4);
        List<Float> sizes = new ArrayList<Float>(4);
        if (options.showSeconds) {
            roles.add(ROLE_SECOND);
            sizes.add(h * 0.13f);
        }
        roles.add(ROLE_WEEKDAY);
        sizes.add(h * 0.15f);
        roles.add(ROLE_MONTH_DAY);
        sizes.add(h * 0.13f);
        roles.add(ROLE_YEAR);
        sizes.add(h * 0.11f);

        float lineGap = h * 0.05f;
        float blockHeight = lineGap * (sizes.size() - 1);
        for (float size : sizes) {
            blockHeight += size;
        }
        float cursor = h / 2f - blockHeight / 2f;
        for (int i = 0; i < sizes.size(); i++) {
            float size = sizes.get(i);
            out.add(new Slot(roles.get(i), sideCenterX, cursor + size / 2f, size, sideBoxWidth, false));
            cursor += size + lineGap;
        }
        return new ClockLayout(true, out);
    }

    private static ClockLayout tall(int w, int h, ClockOptions options) {
        float pad = Math.min(w, h) * PADDING_FRACTION;
        float boxWidth = w - 2f * pad;
        float centerX = w / 2f;

        float dateSize = h * 0.075f;
        float smallSize = h * 0.050f;
        float gap = h * 0.020f;

        // Whatever the small lines and the gaps do not need is split between the hour and the minute.
        float reserved = 2f * pad + dateSize + smallSize + 3f * gap;
        float mainSize = (h - reserved) / 2f;

        List<Slot> out = new ArrayList<Slot>(4);
        float cursor = pad;
        out.add(new Slot(ROLE_HOUR, centerX, cursor + mainSize / 2f, mainSize, boxWidth, true));
        cursor += mainSize + gap;
        out.add(new Slot(ROLE_MINUTE, centerX, cursor + mainSize / 2f, mainSize, boxWidth, true));
        cursor += mainSize + gap;
        out.add(new Slot(ROLE_WEEKDAY_DATE, centerX, cursor + dateSize / 2f, dateSize, boxWidth, false));
        cursor += dateSize + gap;
        out.add(new Slot(ROLE_SMALL_LINE, centerX, cursor + smallSize / 2f, smallSize, boxWidth, false));
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
