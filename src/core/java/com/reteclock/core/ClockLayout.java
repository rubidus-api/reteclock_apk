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
    /**
     * Not a field: the space between two of them.
     *
     * Whitespace separating two fields belongs to neither, so it is measured under this rather than
     * under whichever field happens to sit before it. Otherwise the gap inherits that field's font
     * and decoration — underline the year and the space after it gets underlined too.
     */
    public static final String ROLE_GAP = "gap";

    /**
     * One piece of a line: which field it shows, and what separates it from the piece before it.
     *
     * A line can be several pieces so that each field can be drawn in its own font. A separator is
     * read in two halves: the visible characters belong to the part before it and are drawn in its
     * font, because a comma or a colon is punctuation attached to what it follows; the whitespace
     * belongs to nobody and is reserved as a plain gap. So underlining a field cannot underline the
     * space beside it.
     */
    public static final class Part {
        public final String role;
        public final String separatorBefore;

        Part(String role, String separatorBefore) {
            this.role = role;
            this.separatorBefore = separatorBefore;
        }
    }

    /** One line of text: what it is, where its center sits, how big it is, whether it is bold. */
    public static final class Slot {
        /** The line as a whole. Composite lines keep their old role, so callers still recognise them. */
        public final String role;
        /** The pieces it is drawn from, in order. Always at least one. */
        public final List<Part> parts;
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

        Slot(String role, float centerX, float centerY, float textSize, float maxWidth,
                boolean bold) {
            this(role, singlePart(role), centerX, centerY, textSize, maxWidth, bold);
        }

        Slot(String role, List<Part> parts, float centerX, float centerY, float textSize,
                float maxWidth, boolean bold) {
            this.role = role;
            this.parts = parts;
            this.centerX = centerX;
            this.centerY = centerY;
            this.textSize = textSize;
            this.maxWidth = maxWidth;
            this.bold = bold;
        }
    }

    /** Fraction of a wide screen given to the big time block. */
    private static final float WIDE_MAIN_FRACTION = 0.62f;

    /**
     * Padding kept free on every edge, as a fraction of the shorter edge.
     *
     * Derived from the burn-in amplitude rather than chosen independently: the whole drawing is
     * translated by up to that amplitude, so the padding has to be larger or content would leave
     * the screen (R14). Widening the shift now widens this with it. T011 asserts the invariant.
     */
    private static final float PADDING_FRACTION = BurnInShift.AMPLITUDE_FRACTION * 1.2f;

    private final boolean wide;
    private final List<Slot> slots;
    /** Kept because which strings a field can show depends on them. */
    private final ClockOptions options;

    private ClockLayout(boolean wide, List<Slot> slots, ClockOptions options) {
        this.wide = wide;
        this.slots = slots;
        this.options = options;
    }

    /** True when the wide (landscape) arrangement is used. */
    public boolean isWide() {
        return wide;
    }

    /** The lines to draw, in drawing order. */
    public List<Slot> slots() {
        return slots;
    }

    /**
     * The visible characters of a separator: punctuation, which belongs to the part before it.
     *
     * Public because the view has to draw exactly this much and no more — the whitespace is the
     * plan's business, not something to paint.
     */
    public static String visibleOf(String separator) {
        return separator == null ? "" : separator.trim();
    }

    /** The whitespace of a separator: a gap, belonging to neither field. */
    public static String gapOf(String separator) {
        if (separator == null) {
            return "";
        }
        String visible = separator.trim();
        if (visible.isEmpty()) {
            return separator;
        }
        // Whatever follows the punctuation, e.g. the space in ", ".
        int end = separator.lastIndexOf(visible) + visible.length();
        return separator.substring(end);
    }

    /** One part, for a line that shows a single field. */
    private static List<Part> singlePart(String role) {
        List<Part> one = new ArrayList<Part>(1);
        one.add(new Part(role, ""));
        return one;
    }

    /** A line built from several fields, each able to carry its own font. */
    private static List<Part> parts(String[] roles, String[] separators) {
        List<Part> out = new ArrayList<Part>(roles.length);
        for (int i = 0; i < roles.length; i++) {
            out.add(new Part(roles[i], separators[i]));
        }
        return out;
    }

    /**
     * Padding kept free on every edge, in pixels.
     *
     * Public because the burn-in shift has to stay inside it; T011 checks that it does.
     */
    public static float paddingPx(int widthPx, int heightPx) {
        return Math.min(widthPx, heightPx) * PADDING_FRACTION;
    }

    /**
     * Where each part of a line begins, given how wide each one measured.
     *
     * The parts sit side by side and the group is centred on {@code centerX}, so a line reads as
     * one line however its pieces are shaped. Only the view can measure glyphs, so it supplies the
     * widths and this does the arithmetic — which keeps the arithmetic testable on a JVM.
     */
    public static float[] partOffsets(float centerX, float[] widths) {
        float total = 0f;
        for (float width : widths) {
            total += width;
        }
        float[] starts = new float[widths.length];
        float cursor = centerX - total / 2f;
        for (int i = 0; i < widths.length; i++) {
            starts[i] = cursor;
            cursor += widths[i];
        }
        return starts;
    }

    /**
     * Measures a string for one field at one size. Only the view knows about glyphs.
     *
     * Boldness is passed because it changes the width: the hour and the minute are drawn bold, and
     * measuring them light would size them to something narrower than they are drawn.
     */
    public interface Metrics {
        float width(String role, boolean bold, String text, float textSize);
    }

    /**
     * Sizes and cell positions, worked out once from what each field could ever show.
     *
     * Nothing here depends on the current time, so nothing about the drawing changes as the clock
     * ticks: no resize as the digits change width, no sliding as a line gets narrower. Build one
     * when the fonts, the options or the screen change, and read it every frame.
     */
    public static final class Plan {
        private final java.util.Map<String, Float> sizes = new java.util.HashMap<String, Float>();
        private final java.util.Map<String, float[]> cells = new java.util.HashMap<String, float[]>();
        private final java.util.Map<String, Float> gaps = new java.util.HashMap<String, Float>();

        private static String key(Slot slot, Part part) {
            return slot.role + "/" + part.role;
        }

        /** The size this line is drawn at, small enough that its worst case fits the box. */
        public float textSize(Slot slot) {
            Float size = sizes.get(slot.role);
            return size == null ? slot.textSize : size;
        }

        /** Where this field's cell begins. The cell does not move as the text inside it changes. */
        public float cellStart(Slot slot, Part part) {
            float[] cell = cells.get(key(slot, part));
            return cell == null ? slot.centerX : cell[0];
        }

        /** How wide this field's cell is: its own widest content, drawn at {@link #textSize}. */
        public float cellWidth(Slot slot, Part part) {
            float[] cell = cells.get(key(slot, part));
            return cell == null ? 0f : cell[1];
        }

        /**
         * Space reserved after this field and drawn by nobody.
         *
         * Zero when the next field follows immediately. Nothing is painted here, so no field's
         * decoration can reach it.
         */
        public float gapAfter(Slot slot, Part part) {
            Float gap = gaps.get(key(slot, part));
            return gap == null ? 0f : gap;
        }
    }

    /**
     * Works out how big each line can be and how much room each field in it needs.
     *
     * Each field is measured at its widest — every hour for the hour, every weekday for the weekday
     * — so a line is sized for the worst case rather than for the moment. A line is then shrunk as a
     * whole if that worst case does not fit its box, which keeps the fields on one line in step with
     * each other.
     */
    public Plan plan(Metrics metrics) {
        Plan plan = new Plan();
        for (Slot slot : slots) {
            int count = slot.parts.size();
            float[] widest = new float[count];
            float[] gap = new float[count];
            float total = 0f;
            for (int i = 0; i < count; i++) {
                String separator = i + 1 < count ? slot.parts.get(i + 1).separatorBefore : "";
                widest[i] = widestPart(slot, slot.parts.get(i), visibleOf(separator),
                        slot.textSize, metrics);
                // Measured as nobody's: not under this field's role, so the gap does not move when
                // this field's font changes, and no decoration of it can reach the gap.
                String space = gapOf(separator);
                gap[i] = space.isEmpty() ? 0f
                        : metrics.width(ROLE_GAP, false, space, slot.textSize);
                total += widest[i] + gap[i];
            }

            float size = shrinkToFit(slot.textSize, total, slot.maxWidth);
            if (size != slot.textSize && slot.textSize > 0f) {
                // Widths scale with the size, so the measured worst case scales with it too. That
                // saves measuring every candidate twice for a line that had to shrink.
                float scale = size / slot.textSize;
                total = 0f;
                for (int i = 0; i < count; i++) {
                    widest[i] *= scale;
                    gap[i] *= scale;
                    total += widest[i] + gap[i];
                }
            }
            plan.sizes.put(slot.role, size);

            float cursor = slot.centerX - total / 2f;
            for (int i = 0; i < count; i++) {
                String key = Plan.key(slot, slot.parts.get(i));
                plan.cells.put(key, new float[] {cursor, widest[i]});
                plan.gaps.put(key, gap[i]);
                cursor += widest[i] + gap[i];
            }
        }
        return plan;
    }

    /**
     * The width of the widest thing this part can ever draw, including any punctuation that
     * follows it.
     *
     * Whitespace after the part is not included: that is a gap, measured separately and belonging
     * to no field.
     */
    private float widestPart(Slot slot, final Part part, final String suffix,
            final float textSize, final Metrics metrics) {
        final boolean bold = slot.bold;
        return ClockSamples.widest(ClockSamples.of(part.role, options),
                new ClockSamples.Widths() {
                    @Override
                    public float of(String text) {
                        return metrics.width(part.role, bold, text + suffix, textSize);
                    }
                });
    }

    /** Builds the layout for a screen of the given pixel size. */
    public static ClockLayout of(int widthPx, int heightPx, ClockOptions options) {
        return widthPx > heightPx
                ? wide(widthPx, heightPx, options)
                : tall(widthPx, heightPx, options);
    }

    private static ClockLayout wide(int w, int h, ClockOptions options) {
        float pad = paddingPx(w, h);
        float mainWidth = w * WIDE_MAIN_FRACTION;
        float sideWidth = w - mainWidth;
        float sideCenterX = mainWidth + sideWidth / 2f;
        float sideBoxWidth = sideWidth - 2f * pad;

        List<Slot> out = new ArrayList<Slot>(5);

        // The big time takes the full height between the paddings; the view scales it down only if
        // it would be wider than its half of the screen.
        out.add(new Slot(ROLE_HOUR_MINUTE,
                parts(new String[] {ROLE_HOUR, ROLE_MINUTE}, new String[] {"", ":"}),
                mainWidth / 2f, h / 2f, h - 2f * pad, mainWidth - 2f * pad, true));

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
        return new ClockLayout(true, out, options);
    }

    private static ClockLayout tall(int w, int h, ClockOptions options) {
        float pad = paddingPx(w, h);
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
        out.add(new Slot(ROLE_WEEKDAY_DATE,
                parts(new String[] {ROLE_WEEKDAY, ROLE_MONTH_DAY}, new String[] {"", ", "}),
                centerX, cursor + dateSize / 2f, dateSize, boxWidth, false));
        cursor += dateSize + gap;
        List<Part> smallParts = options.showSeconds
                ? parts(new String[] {ROLE_YEAR, ROLE_SECOND}, new String[] {"", "   "})
                : singlePart(ROLE_YEAR);
        out.add(new Slot(ROLE_SMALL_LINE, smallParts,
                centerX, cursor + smallSize / 2f, smallSize, boxWidth, false));
        return new ClockLayout(false, out, options);
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
