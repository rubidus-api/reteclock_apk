package com.reteclock.core.layout;

import com.reteclock.core.ClockLayout;
import com.reteclock.core.ClockOptions;
import com.reteclock.core.ClockSamples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a composed layout comes to on a screen of a given size (RFC-0005, R90).
 *
 * Four steps, in order: **measure** what each field would need, **place** each box from its anchor,
 * **fit** the text to the box it was given, and **report** what is wrong with the arrangement. The
 * drawing code stays as dumb as it is today: it is handed a rectangle and a text size per box.
 *
 * Two rules are older than this engine and are kept exactly:
 *
 * - **Nothing is clipped** (R23). A box smaller than the widest thing its field can ever draw does
 *   not cut the text off; the text comes down in size until it fits, the same way
 *   {@link ClockLayout#shrinkToFit} has always done it. Every field is measured at its worst case
 *   rather than at what it happens to say now, so nothing resizes as the clock ticks.
 * - **Only the view measures glyphs.** This asks through {@link ClockLayout.Metrics}, as the old
 *   layout does.
 *
 * Complaints are values, not log lines, because the editor shows them while the user is still on the
 * screen (D3). Overlap and running off the edge are *allowed*: it is the user's screen and a clock
 * deliberately overlapping its own seconds is a legitimate design. Being told is not being overruled.
 */
public final class BoxPlan {

    /** The text had to come down in size to fit the box it was given. */
    public static final int SHRUNK = 0;
    /** Part of the box is outside the screen. */
    public static final int OFF_SCREEN = 1;
    /** Two boxes cover some of the same ground. */
    public static final int OVERLAP = 2;
    /** Nothing at all would be drawn — the one mistake with no way back from the clock face. */
    public static final int NOTHING_DRAWN = 3;

    /**
     * The height a box arrives at when nothing has said otherwise, as a share of the shorter edge.
     *
     * A field has no natural *height*: a string is drawn at whatever size it is asked for. So a new
     * box has to start somewhere, and this is that somewhere — big enough to read across a room,
     * small enough that four of them fit on a phone. The user then drags it.
     */
    public static final float DEFAULT_HEIGHT_SHARE = 0.12f;

    /** Below this a field is not a clock any more, and the engine says so rather than drawing it. */
    private static final float TOO_SMALL_PX = 6f;

    /** One box, placed. */
    public static final class Placed {
        /** Which field is drawn here. */
        public final String field;
        /** Left, top, width, height, in pixels. */
        public final float[] rect;
        /** The size the text is drawn at, after fitting. */
        public final float textSize;
        /** Where the field's own drawing sits inside the rectangle — one of {@link Anchor}'s nine. */
        public final int align;

        Placed(String field, float[] rect, float textSize, int align) {
            this.field = field;
            this.rect = rect;
            this.textSize = textSize;
            this.align = align;
        }
    }

    /** Something the arrangement does that the user should be told about. */
    public static final class Complaint {
        public final int kind;
        /** The field it is about, or null for a complaint about the layout as a whole. */
        public final String field;
        /** The other field, where the complaint is about a pair. */
        public final String other;
        /** How much the text had to shrink, as a fraction of what it asked for; 1 when it did not. */
        public final float ratio;

        Complaint(int kind, String field, String other, float ratio) {
            this.kind = kind;
            this.field = field;
            this.other = other;
            this.ratio = ratio;
        }
    }

    private final List<Placed> placed;
    private final List<Complaint> complaints;

    private BoxPlan(List<Placed> placed, List<Complaint> complaints) {
        this.placed = Collections.unmodifiableList(placed);
        this.complaints = Collections.unmodifiableList(complaints);
    }

    public List<Placed> placed() {
        return placed;
    }

    public List<Complaint> complaints() {
        return complaints;
    }

    /** Works out where everything goes on a screen of this size. */
    public static BoxPlan of(List<LayoutBox> boxes, int screenW, int screenH,
            ClockOptions options, ClockLayout.Metrics metrics) {
        List<Placed> out = new ArrayList<Placed>();
        List<Complaint> said = new ArrayList<Complaint>();
        float defaultHeight = Math.min(screenW, screenH) * DEFAULT_HEIGHT_SHARE;

        for (LayoutBox box : boxes) {
            if (box == null || !box.shown) {
                continue;
            }
            // The box's height is the size its field is drawn at: a line of text is as tall as its
            // type. Width is either the box's own or whatever that type needs.
            float height = box.heightOn(screenH, defaultHeight);
            float textSize = height;
            float needed = widestOf(box.field, options, metrics, textSize);
            float width = box.widthOn(screenW, needed);

            if (needed > width && width > 0f) {
                float fitted = ClockLayout.shrinkToFit(textSize, needed, width);
                said.add(new Complaint(SHRUNK, box.field, null,
                        textSize > 0f ? fitted / textSize : 1f));
                textSize = fitted;
            }
            if (textSize < TOO_SMALL_PX) {
                said.add(new Complaint(SHRUNK, box.field, null, 0f));
            }

            float[] rect = box.rectOn(screenW, screenH, width, height);
            if (rect[0] < -0.5f || rect[1] < -0.5f
                    || rect[0] + rect[2] > screenW + 0.5f
                    || rect[1] + rect[3] > screenH + 0.5f) {
                said.add(new Complaint(OFF_SCREEN, box.field, null, 1f));
            }
            out.add(new Placed(box.field, rect, textSize, box.align));
        }

        for (int i = 0; i < out.size(); i++) {
            for (int j = i + 1; j < out.size(); j++) {
                if (overlaps(out.get(i).rect, out.get(j).rect)) {
                    said.add(new Complaint(OVERLAP, out.get(i).field, out.get(j).field, 1f));
                }
            }
        }
        if (out.isEmpty()) {
            said.add(new Complaint(NOTHING_DRAWN, null, null, 1f));
        }
        return new BoxPlan(out, said);
    }

    /**
     * The widest this field can ever draw at this size — its worst case, not what it says now.
     *
     * Public because the tests and the editor both want to ask it: "will this fit" is the question
     * behind every complaint, and it should be asked the same way everywhere.
     */
    public static float widestOf(String field, ClockOptions options,
            final ClockLayout.Metrics metrics, final float textSize) {
        final String role = field;
        return ClockSamples.widest(ClockSamples.of(field, options), new ClockSamples.Widths() {
            @Override
            public float of(String text) {
                return metrics.width(role, text, textSize);
            }
        });
    }

    /** Touching edge to edge is not overlapping; a shared pixel is. */
    private static boolean overlaps(float[] a, float[] b) {
        return a[0] < b[0] + b[2] - 0.001f
                && b[0] < a[0] + a[2] - 0.001f
                && a[1] < b[1] + b[3] - 0.001f
                && b[1] < a[1] + a[3] - 0.001f;
    }
}
