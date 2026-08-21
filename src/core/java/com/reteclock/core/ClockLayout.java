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
 *   |                           |  Jul 12      |
 *   |                           |  2026        |
 *   +---------------------------+--------------+
 *
 * Tall screens (height >= width):
 *
 *   13            (hour)
 *   45            (minute)
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
     * AM, PM, NN or MN on a twelve-hour clock — a field of its own, not a decoration.
     *
     * Where the hour and the minute share a line it is drawn just past the minute, sitting on the
     * same baseline, and the line's box is narrowed to leave room for it. Where they are stacked it
     * is a line of its own under the minute, and the layout gives it height like any other line.
     * Either way the space is reserved rather than borrowed, so nothing it sits beside has to move
     * when the marker changes from AM to PM.
     */
    public static final String ROLE_MERIDIEM = "meridiem";

    /** How much of a stacked time's own height the marker line takes. */
    private static final float MERIDIEM_SHARE = 0.22f;

    /** And how much of a one-line time's width is kept back for it. */
    private static final float MERIDIEM_WIDTH_SHARE = 0.15f;
    /**
     * How much of the gap between two big lines is left above the marker under them.
     *
     * A marker is part of the time, not a line beside it: at the full gap it sits away on its own
     * near the foot of the screen, which is how it reads as a caption rather than as the other half
     * of "2:29 AM".
     */
    private static final float MERIDIEM_GAP_SHARE = 0.35f;
    /**
     * How much room the marker needs beside the time, as a share of the time's own size.
     *
     * The marker is drawn at 0.30 of the time and set off from it by a third of its own size, so
     * two glyphs and their gap come to about this much. The ordinary wide layout keeps back a share
     * of the *width* instead, which is right there because the side column is beyond it; with only
     * the time on the screen there is nothing beyond it, and a share of the width that came out
     * smaller than the marker left it squeezed against the edge.
     */
    private static final float MERIDIEM_ROOM_SHARE = 0.44f;
    /** And never more than this much of the width, whatever the height says. */
    private static final float MERIDIEM_ROOM_CAP = 0.30f;

    /** The width a time on one line may use, once the marker has been allowed for. */
    /**
     * A wide screen with the time and nothing else: one line, the whole screen (issue #31).
     *
     * The share of the width the time usually takes is what leaves room for the date column beside
     * it. There is no column now, so there is no share: the time is centred on the screen and has
     * all of it, less the padding — and, on a twelve-hour clock, less the room the marker needs
     * beside it, which is the same allowance the ordinary layout makes.
     */
    private static ClockLayout wideTimeOnly(int w, int h, ClockOptions options, float pad) {
        float room = w - 2f * pad;
        // The room kept for the marker is measured from the time's own size, not from the width:
        // the marker is drawn in proportion to the time, and a slice of the width can be smaller
        // than the marker on a wide, short screen — which is what squeezed it against the edge.
        float reserve = options.showsMeridiem()
                ? Math.min(room * MERIDIEM_ROOM_CAP, (h - 2f * pad) * MERIDIEM_ROOM_SHARE)
                : 0f;
        float box = room - reserve;
        // The marker sits to the right of the line, in a slice the layout keeps back for it. With
        // the date column gone there is nothing to the right *but* that slice, so the time is
        // centred on what is left rather than on the screen — otherwise the pair is off-centre and
        // the marker runs past the edge and is clipped.
        float centerX = w / 2f - reserve / 2f;
        List<Slot> out = new ArrayList<Slot>(1);
        Slot line = new Slot(ROLE_HOUR_MINUTE,
                parts(new String[] {ROLE_HOUR, ROLE_MINUTE}, new String[] {"", ":"}),
                centerX, h / 2f, h - 2f * pad, box);
        // The marker may use the whole of the room kept for it, and no more: its right edge is the
        // padding, which is where every other thing on this screen stops.
        line.rightLimit = centerX + box / 2f + reserve;
        out.add(line);
        return new ClockLayout(true, out, options, null);
    }

    /**
     * A tall screen with the time and nothing else: the hour over the minute, and the marker under
     * them where the clock reads in twelve hours.
     *
     * The two lines take the height the time and the date used to divide between them, so they are
     * larger than they have ever been on this screen — which is the point of the option.
     */
    private static ClockLayout tallTimeOnly(int w, int h, ClockOptions options, float pad,
            float boxWidth, float centerX, float gap) {
        boolean marker = options.showsMeridiem();
        // The marker belongs to the time rather than being a third line of its own, so it follows
        // the minute at a fraction of the gap the two big lines keep between them. At the full gap
        // it drifts towards the bottom of the screen and reads as something else entirely.
        float markerGap = gap * MERIDIEM_GAP_SHARE;
        float content = h - 2f * pad - gap - (marker ? markerGap : 0f);
        float mainSize = marker ? content / (2f + MERIDIEM_SHARE) : content / 2f;
        float meridiemSize = marker ? mainSize * MERIDIEM_SHARE : 0f;

        List<Slot> out = new ArrayList<Slot>(3);
        float cursor = pad;
        out.add(new Slot(ROLE_HOUR, centerX, cursor + mainSize / 2f, mainSize, boxWidth));
        cursor += mainSize + gap;
        out.add(new Slot(ROLE_MINUTE, centerX, cursor + mainSize / 2f, mainSize, boxWidth));
        if (marker) {
            cursor += mainSize + markerGap;
            out.add(new Slot(ROLE_MERIDIEM, centerX, cursor + meridiemSize / 2f,
                    meridiemSize, boxWidth));
        }
        return new ClockLayout(false, out, options, null);
    }

    private static float timeBoxWidth(float boxWidth, ClockOptions options) {
        return options.showsMeridiem() ? boxWidth * (1f - MERIDIEM_WIDTH_SHARE) : boxWidth;
    }

    /**
     * How far right the AM/PM marker may reach on a one-line time.
     *
     * The time's own box stops short of it: {@link #timeBoxWidth} narrows the box precisely so the
     * marker has somewhere to go. The marker is therefore drawn *outside* the box, and the edge it
     * must not cross is the box plus the strip that was kept back — not the box, which is where the
     * digits end.
     */
    public static float meridiemLimit(Slot slot) {
        if (slot.rightLimit > 0f) {
            return slot.rightLimit;
        }
        return slot.centerX + slot.maxWidth / 2f
                + slot.maxWidth * MERIDIEM_WIDTH_SHARE / (1f - MERIDIEM_WIDTH_SHARE);
    }
    /** The saying along the bottom, which chooses its own font and decoration like any line. */
    public static final String ROLE_QUOTE = "quote";

    /**
     * The three parts of the month grid, which are styled but never laid out as slots.
     *
     * The grid places itself inside the rectangle the layout hands it, so these name a style rather
     * than a position: the line with the month and the year in it, the row of weekday headings, and
     * the day numbers. They are fields to the settings screen like any other.
     */
    public static final String ROLE_CALENDAR_TITLE = "calendar_title";
    public static final String ROLE_CALENDAR_WEEKDAY = "calendar_weekday";
    public static final String ROLE_CALENDAR_DAY = "calendar_day";
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

    /**
     * One line of text: what it is, where its center sits, and how big it is.
     *
     * Deliberately says nothing about weight. Bold is a decoration the user sets per field, so the
     * layout imposing one would mean two places decided it and the user could not turn it off.
     */
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
        /**
         * Lines that must end up the same size as each other, or null for a line that answers only
         * to itself. Sizing is per line — a line too wide for its box is shrunk — so without this a
         * column meant to be level would come apart the moment one of its lines needed shrinking.
         */
        final String sizeGroup;
        /**
         * How far right the marker beside this line may reach, or 0 for "work it out".
         *
         * The room kept for the marker is the layout's business and it is not always the same
         * share: with only the time on the screen it follows the marker's own size rather than a
         * slice of the width. Carrying it on the line keeps the drawing code from having to know
         * which layout it is in.
         */
        public float rightLimit;

        Slot(String role, float centerX, float centerY, float textSize, float maxWidth) {
            this(role, singlePart(role), centerX, centerY, textSize, maxWidth, null);
        }

        Slot(String role, float centerX, float centerY, float textSize, float maxWidth,
                String sizeGroup) {
            this(role, singlePart(role), centerX, centerY, textSize, maxWidth, sizeGroup);
        }

        Slot(String role, List<Part> parts, float centerX, float centerY, float textSize,
                float maxWidth) {
            this(role, parts, centerX, centerY, textSize, maxWidth, null);
        }

        Slot(String role, List<Part> parts, float centerX, float centerY, float textSize,
                float maxWidth, String sizeGroup) {
            this.role = role;
            this.parts = parts;
            this.centerX = centerX;
            this.centerY = centerY;
            this.textSize = textSize;
            this.maxWidth = maxWidth;
            this.sizeGroup = sizeGroup;
        }
    }

    /**
     * The side column's line proportions, relative to each other, and the gap between the lines.
     *
     * Every line is the same: seconds, weekday, month and day, year. They used to be four different
     * sizes in a fixed hierarchy — 0.13, 0.15, 0.13, 0.11 — which read as an accident rather than a
     * decision, and the owner asked for one size. Only the ratio to the gap matters now: the block
     * is scaled as a whole to fill the height its share of the screen offers, so a wider side
     * region means bigger lines, up to exactly that height and no further.
     */
    private static final float SIDE_LINE = 0.13f;
    private static final float SIDE_LINE_GAP = 0.05f;

    /** The name under which the four side lines agree to be one size. */
    private static final String SIDE_GROUP = "side";

    /** The date and small lines' split of what the tall time leaves, 0.075 : 0.050 as always. */
    private static final float TALL_DATE_SHARE = 0.6f;

    /**
     * How much of a wide screen's height the calendar takes, leaving the clock the rest.
     *
     * The time moves out of the middle and up into what is left, which is what the calendar being
     * "underneath" amounts to: nothing is repositioned by hand, the clock is simply given a shorter
     * screen to lay itself out in.
     */
    private static final float CALENDAR_SHARE_WIDE = 0.5f;

    /**
     * How tall the saying's strip is, as a share of the screen's shorter edge.
     *
     * Half again as deep as the timer's strip, which is eight per cent of the same edge. It was
     * nine, matching the timer almost exactly, and that turned out to be the wrong thing to match:
     * a bar and three short numbers need a band, but a sentence of thirty words needs room to be
     * read, and the lettering can only ever be a third of whatever this is.
     */
    private static final float QUOTE_SHARE = 0.13f;

    /**
     * On a tall screen the time and the calendar together take exactly the share the time used to
     * take alone, so switching the calendar on does not push the date and the year about. Within
     * that share, this much is the time and the rest is the grid.
     */
    private static final float CALENDAR_TIME_SHARE_TALL = 0.32f;

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

    private final float[] calendarRect;
    private float[] quoteRect;

    private ClockLayout(boolean wide, List<Slot> slots, ClockOptions options, float[] calendar) {
        this.wide = wide;
        this.slots = slots;
        this.options = options;
        this.calendarRect = calendar;
    }

    /** True when the wide (landscape) arrangement is used. */
    /** Where the calendar goes: left, top, width, height — or null when there is no calendar. */
    public float[] calendarRect() {
        return calendarRect;
    }

    /** Where the saying goes: left, top, width, height — or null when there is none. */
    public float[] quoteRect() {
        return quoteRect;
    }

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
     * Weight and slant are not passed: they are the user's per-field decorations, and the view
     * applies the same ones here that it applies when drawing. Measuring under a different weight
     * from the one drawn would size a field to something narrower than it appears.
     */
    public interface Metrics {
        float width(String role, String text, float textSize);
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
        // Measured once per line, at the line's unshrunk size; the widths scale with the size, so
        // a line that has to shrink needs no second measuring pass.
        java.util.Map<String, float[]> widests = new java.util.HashMap<String, float[]>();
        java.util.Map<String, float[]> gaps = new java.util.HashMap<String, float[]>();
        java.util.Map<String, Float> fitted = new java.util.HashMap<String, Float>();

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
                        : metrics.width(ROLE_GAP, space, slot.textSize);
                total += widest[i] + gap[i];
            }
            widests.put(slot.role, widest);
            gaps.put(slot.role, gap);
            fitted.put(slot.role, shrinkToFit(slot.textSize, total, slot.maxWidth));
        }

        // Lines that belong to a group take the smallest size any of them could manage, so a
        // column meant to be level stays level even when one of its lines had to give way.
        java.util.Map<String, Float> groupSize = new java.util.HashMap<String, Float>();
        for (Slot slot : slots) {
            if (slot.sizeGroup == null) {
                continue;
            }
            Float smallest = groupSize.get(slot.sizeGroup);
            float mine = fitted.get(slot.role);
            if (smallest == null || mine < smallest) {
                groupSize.put(slot.sizeGroup, mine);
            }
        }

        for (Slot slot : slots) {
            float size = slot.sizeGroup == null
                    ? fitted.get(slot.role)
                    : groupSize.get(slot.sizeGroup);
            float[] widest = widests.get(slot.role).clone();
            float[] gap = gaps.get(slot.role).clone();
            int count = slot.parts.size();

            float total = 0f;
            float scale = slot.textSize > 0f ? size / slot.textSize : 1f;
            for (int i = 0; i < count; i++) {
                widest[i] *= scale;
                gap[i] *= scale;
                total += widest[i] + gap[i];
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
        return ClockSamples.widest(ClockSamples.of(part.role, options),
                new ClockSamples.Widths() {
                    @Override
                    public float of(String text) {
                        return metrics.width(part.role, text + suffix, textSize);
                    }
                });
    }

    /** Builds the layout for a screen of the given pixel size. */
    public static ClockLayout of(int widthPx, int heightPx, ClockOptions options) {
        // The saying takes a strip off the bottom and the clock lays itself out in what is left,
        // exactly as it does for the timer: nothing else has to know the strip is there.
        float quoteHeight = options.quote && !options.timeOnly
                ? Math.min(widthPx, heightPx) * QUOTE_SHARE
                : 0f;
        int usable = Math.max(1, Math.round(heightPx - quoteHeight));
        ClockLayout layout = widthPx > usable
                ? wide(widthPx, usable, options)
                : tall(widthPx, usable, options);
        if (quoteHeight > 0f) {
            // The same air at the foot as everywhere else: text flush against the edge of a screen
            // reads as text that has been cut off.
            float pad = paddingPx(widthPx, heightPx);
            layout.quoteRect = new float[] {
                pad, usable, widthPx - 2f * pad, Math.max(1f, quoteHeight - pad),
            };
        }
        return layout;
    }

    private static ClockLayout wide(int w, int h, ClockOptions options) {
        float pad = paddingPx(w, h);
        if (options.timeOnly) {
            return wideTimeOnly(w, h, options, pad);
        }
        // The clock is laid out in a shorter screen and the calendar takes the floor it stands on.
        if (options.calendar) {
            return wideWithCalendar(w, h, options, pad);
        }
        float[] calendar = null;
        // The user's dial: how much of the width belongs to the big time. The rest is the side
        // column's region, and its lines grow with it.
        float mainWidth = w * options.timeFractionWide;
        float sideWidth = w - mainWidth;
        float sideCenterX = mainWidth + sideWidth / 2f;
        float sideBoxWidth = sideWidth - 2f * pad;

        List<Slot> out = new ArrayList<Slot>(5);

        // The big time takes the full height between the paddings; the view scales it down only if
        // it would be wider than its share of the screen.
        out.add(new Slot(ROLE_HOUR_MINUTE,
                parts(new String[] {ROLE_HOUR, ROLE_MINUTE}, new String[] {"", ":"}),
                mainWidth / 2f, h / 2f, h - 2f * pad,
                timeBoxWidth(mainWidth - 2f * pad, options)));

        List<String> roles = new ArrayList<String>(4);
        List<Float> sizes = new ArrayList<Float>(4);
        if (options.showSeconds) {
            roles.add(ROLE_SECOND);
            sizes.add(SIDE_LINE);
        }
        roles.add(ROLE_WEEKDAY);
        sizes.add(SIDE_LINE);
        roles.add(ROLE_MONTH_DAY);
        sizes.add(SIDE_LINE);
        roles.add(ROLE_YEAR);
        sizes.add(SIDE_LINE);

        // The block is scaled, proportions intact, to exactly the height between the paddings:
        // the lines are as big as their region allows and no bigger. A line still too wide for
        // the side's width is shrunk by the plan, the same way it always was.
        float proportionTotal = SIDE_LINE_GAP * (sizes.size() - 1);
        for (float size : sizes) {
            proportionTotal += size;
        }
        float scale = (h - 2f * pad) / proportionTotal;
        float lineGap = SIDE_LINE_GAP * scale;
        float cursor = pad;
        for (int i = 0; i < sizes.size(); i++) {
            float size = sizes.get(i) * scale;
            out.add(new Slot(roles.get(i), sideCenterX, cursor + size / 2f, size, sideBoxWidth,
                    SIDE_GROUP));
            cursor += size + lineGap;
        }
        return new ClockLayout(true, out, options, calendar);
    }

    /**
     * The wide arrangement with a month beside the clock.
     *
     * The calendar takes the side column's place — the seconds, weekday, date and year lived
     * there, and the calendar says three of those four better than a line of text can. What is
     * left on the left is the time: the hour and the minute on their own lines, one above the
     * other, and the seconds smaller underneath. The dial that splits the screen still splits it,
     * so the user's own proportion goes on working with the calendar as it did without.
     */
    private static ClockLayout wideWithCalendar(int w, int h, ClockOptions options, float pad) {
        float mainWidth = w * options.timeFractionWide;
        float boxWidth = mainWidth - 2f * pad;
        float centerX = mainWidth / 2f;
        float gap = h * 0.02f;

        float content = h - 2f * pad - 2f * gap;
        float bigSize = content * (options.showSeconds ? 0.40f : 0.50f);
        float secondSize = content * 0.20f;
        // The marker is a line, so it costs height: the digits give it up rather than the seconds.
        float meridiemSize = options.hour12 ? bigSize * MERIDIEM_SHARE : 0f;
        if (options.hour12) {
            bigSize -= meridiemSize / 2f;
        }

        List<Slot> out = new ArrayList<Slot>(4);
        float cursor = pad;
        out.add(new Slot(ROLE_HOUR, centerX, cursor + bigSize / 2f, bigSize, boxWidth));
        cursor += bigSize + gap;
        out.add(new Slot(ROLE_MINUTE, centerX, cursor + bigSize / 2f, bigSize, boxWidth));
        cursor += bigSize + gap;
        if (options.hour12) {
            out.add(new Slot(ROLE_MERIDIEM, centerX, cursor + meridiemSize / 2f,
                    meridiemSize, boxWidth));
            cursor += meridiemSize + gap;
        }
        if (options.showSeconds) {
            out.add(new Slot(ROLE_SECOND, centerX, cursor + secondSize / 2f, secondSize, boxWidth));
        }

        float[] calendar = new float[] {
            mainWidth + pad, pad, w - mainWidth - 2f * pad, h - 2f * pad,
        };
        return new ClockLayout(true, out, options, calendar);
    }

    private static ClockLayout tall(int w, int h, ClockOptions options) {
        float pad = paddingPx(w, h);
        float boxWidth = w - 2f * pad;
        float centerX = w / 2f;

        float gap = h * 0.020f;

        // The user's dial: the content height — everything between the paddings that is not a
        // gap — is split between the time and the rest at the chosen fraction. Each side fills
        // its share exactly; a line too wide for the screen is shrunk by the plan as always,
        // which is where the growing stops.
        float content = h - 2f * pad - 3f * gap;

        if (options.timeOnly) {
            return tallTimeOnly(w, h, options, pad, boxWidth, centerX, gap);
        }
        if (options.calendar) {
            return tallWithCalendar(w, h, options, pad, boxWidth, centerX, gap, content);
        }

        // Two big lines, and a small one under them when the clock is on twelve hours. The marker
        // comes out of the time's own share, so everything below it stays where it was.
        float timeShare = content * options.timeFractionTall;
        float mainSize = options.hour12
                ? (timeShare - gap) / (2f + MERIDIEM_SHARE)
                : timeShare / 2f;
        float meridiemSize = options.hour12 ? mainSize * MERIDIEM_SHARE : 0f;
        float rest = content * (1f - options.timeFractionTall);
        float dateSize = rest * TALL_DATE_SHARE;
        float smallSize = rest * (1f - TALL_DATE_SHARE);

        List<Slot> out = new ArrayList<Slot>(4);
        float cursor = pad;
        out.add(new Slot(ROLE_HOUR, centerX, cursor + mainSize / 2f, mainSize, boxWidth));
        cursor += mainSize + gap;
        out.add(new Slot(ROLE_MINUTE, centerX, cursor + mainSize / 2f, mainSize, boxWidth));
        cursor += mainSize + gap;
        if (options.hour12) {
            out.add(new Slot(ROLE_MERIDIEM, centerX, cursor + meridiemSize / 2f,
                    meridiemSize, boxWidth));
            cursor += meridiemSize + gap;
        }
        out.add(new Slot(ROLE_WEEKDAY_DATE,
                parts(new String[] {ROLE_WEEKDAY, ROLE_MONTH_DAY}, new String[] {"", ", "}),
                centerX, cursor + dateSize / 2f, dateSize, boxWidth));
        cursor += dateSize + gap;
        List<Part> smallParts = options.showSeconds
                ? parts(new String[] {ROLE_YEAR, ROLE_SECOND}, new String[] {"", "   "})
                : singlePart(ROLE_YEAR);
        out.add(new Slot(ROLE_SMALL_LINE, smallParts,
                centerX, cursor + smallSize / 2f, smallSize, boxWidth));
        return new ClockLayout(false, out, options, null);
    }

    /**
     * The tall layout with a month under the time.
     *
     * Two things go, because the calendar already says them better than a line of text can: the
     * weekday with the date, and the year beside the seconds. What is left is the time on one
     * line — hour and minute together, as wide as the screen allows rather than stacked — then the
     * grid, then the seconds if they are wanted at all.
     *
     * The time and the grid together take exactly the share the time alone used to take, so
     * turning the calendar on does not move what remains below it.
     */
    private static ClockLayout tallWithCalendar(int w, int h, ClockOptions options, float pad,
            float boxWidth, float centerX, float gap, float content) {
        float together = content * options.timeFractionTall;
        float timeSize = together * CALENDAR_TIME_SHARE_TALL;
        float gridHeight = together - timeSize;
        float smallSize = content * (1f - options.timeFractionTall) * (1f - TALL_DATE_SHARE);

        List<Slot> out = new ArrayList<Slot>(2);
        float cursor = pad;
        out.add(new Slot(ROLE_HOUR_MINUTE,
                parts(new String[] {ROLE_HOUR, ROLE_MINUTE}, new String[] {"", ":"}),
                centerX, cursor + timeSize / 2f, timeSize, timeBoxWidth(boxWidth, options)));
        cursor += timeSize + gap;

        float[] calendar = new float[] {pad, cursor, boxWidth, gridHeight};
        cursor += gridHeight + gap;

        if (options.showSeconds) {
            out.add(new Slot(ROLE_SMALL_LINE, singlePart(ROLE_SECOND),
                    centerX, cursor + smallSize / 2f, smallSize, boxWidth));
        }
        return new ClockLayout(false, out, options, calendar);
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
