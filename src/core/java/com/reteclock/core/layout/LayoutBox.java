package com.reteclock.core.layout;

/**
 * One box of a composed layout: a field, where it sits, how big it is, and how it behaves (RFC-0005,
 * R89).
 *
 * A value. Every change answers a new box and leaves the old one alone, which is what lets the
 * editor keep an undo stack that is just a list of layouts.
 *
 * Positions and sizes are **fractions of the screen**, not pixels: a layout is drawn on one screen
 * and may be shown on another — the same phone turned, or the same settings file carried to a new
 * phone through Import/Export. A size may also be *natural*, meaning as large as the field asks to
 * be, which is what most boxes want most of the time.
 *
 * The text form goes into the settings file, so it can come back short, long, mistyped or edited by
 * hand. Like {@link com.reteclock.core.DateOrder}, a box repairs what it is given rather than
 * obeying it: a clock that draws nothing because a file was mistyped is a fault the user cannot undo
 * from the screen. Only one thing is refused outright — a line that names no field, which is not a
 * box at all.
 */
public final class LayoutBox {

    /** A width or a height of this much means "as large as the field wants to be". */
    public static final float NATURAL = -1f;

    /** Which field this box draws: one of the roles in {@code ClockLayout}. */
    public final String field;
    /** Which of the nine points of the screen the box is measured from. */
    public final int anchor;
    /** Offset from that anchor, as a fraction of the screen; inward from an anchored edge. */
    public final float x;
    public final float y;
    /** Size as a fraction of the screen, or {@link #NATURAL}. */
    public final float width;
    public final float height;
    /** Where the field's own drawing sits inside this box — the same nine points. */
    public final int align;
    /** Whether the editor may move it. A locked box is still drawn. */
    public final boolean locked;
    /** Whether it is on the layout at all. A hidden box keeps its place for when it comes back. */
    public final boolean shown;
    /**
     * The edge this box takes whole, or {@link Strips#NONE} for an ordinary box (D9).
     *
     * Only the timer's strip and the saying use it. It is stated rather than guessed from the
     * anchor, because "anchored to the foot" and "is a strip along the foot" are different claims:
     * the app's own arrangement puts the saying at the foot *with padding round it*, and that is an
     * ordinary box that happens to be low down.
     */
    public final int edge;

    private LayoutBox(String field, int anchor, float x, float y, float width, float height,
            int align, boolean locked, boolean shown) {
        this(field, anchor, x, y, width, height, align, locked, shown, Strips.NONE);
    }

    private LayoutBox(String field, int anchor, float x, float y, float width, float height,
            int align, boolean locked, boolean shown, int edge) {
        this.edge = edge < Strips.TOP || edge > Strips.RIGHT ? Strips.NONE : edge;
        this.field = field;
        this.anchor = anchor < 0 || anchor >= Anchor.COUNT ? Anchor.MIDDLE_CENTRE : anchor;
        this.align = align < 0 || align >= Anchor.COUNT ? Anchor.MIDDLE_CENTRE : align;
        this.x = clampOffset(x);
        this.y = clampOffset(y);
        this.width = clampSize(width);
        this.height = clampSize(height);
        this.locked = locked;
        this.shown = shown;
    }

    /** A box for this field, centred, at its natural size — the state a new box starts in. */
    public static LayoutBox of(String field) {
        return new LayoutBox(field, Anchor.MIDDLE_CENTRE, 0f, 0f, NATURAL, NATURAL,
                Anchor.MIDDLE_CENTRE, false, true);
    }

    /** The same box, measured from another anchor and offset. */
    public LayoutBox at(int anchor, float x, float y) {
        return new LayoutBox(field, anchor, x, y, width, height, align, locked, shown, edge);
    }

    /** The same box at another size; {@link #NATURAL} for either gives that axis back to the field. */
    public LayoutBox sized(float width, float height) {
        return new LayoutBox(field, anchor, x, y, width, height, align, locked, shown, edge);
    }

    /** The same box with its contents sitting somewhere else inside it. */
    public LayoutBox aligned(int align) {
        return new LayoutBox(field, anchor, x, y, width, height, align, locked, shown, edge);
    }

    public LayoutBox locked(boolean locked) {
        return new LayoutBox(field, anchor, x, y, width, height, align, locked, shown, edge);
    }

    public LayoutBox shown(boolean shown) {
        return new LayoutBox(field, anchor, x, y, width, height, align, locked, shown, edge);
    }

    /** The same box taking a whole edge, or {@link Strips#NONE} to make it an ordinary box again. */
    public LayoutBox onEdge(int edge) {
        return new LayoutBox(field, anchor, x, y, width, height, align, locked, shown, edge);
    }

    /** Whether this box is a strip along an edge rather than a rectangle among the others. */
    public boolean isStrip() {
        return edge != Strips.NONE;
    }

    /** Whether the field decides this axis rather than the box. */
    public boolean naturalWidth() {
        return width == NATURAL;
    }

    public boolean naturalHeight() {
        return height == NATURAL;
    }

    /**
     * Where this box lands on a screen of the given pixel size, once its size in pixels is known.
     *
     * The size is passed in rather than read off the box because a natural axis is only known after
     * the field has been measured, and measuring is the view's job.
     *
     * @return left, top, width, height
     */
    public float[] rectOn(int screenW, int screenH, float widthPx, float heightPx) {
        return Anchor.rect(anchor, x * screenW, y * screenH, widthPx, heightPx, screenW, screenH);
    }

    /**
     * The same box, sitting at this rectangle on a screen of this size.
     *
     * The anchor is kept. It is the user's statement about what the box is measured from — a
     * corner, an edge, the middle — and dragging the box somewhere is not a change of mind about
     * that; a box anchored to the bottom of the screen should still be anchored there after it has
     * been nudged. So the offset is worked out for the anchor the box already has.
     *
     * @param rect left, top, width, height, in pixels
     */
    public LayoutBox placedAt(float[] rect, int screenW, int screenH) {
        float x = Anchor.offsetX(anchor, rect[0], rect[2], screenW) / screenW;
        float y = Anchor.offsetY(anchor, rect[1], rect[3], screenH) / screenH;
        return at(anchor, x, y).sized(rect[2] / screenW, rect[3] / screenH);
    }

    /** The pixel width this box asks for, or {@code natural} where it leaves that to the field. */
    public float widthOn(int screenW, float natural) {
        return naturalWidth() ? natural : width * screenW;
    }

    public float heightOn(int screenH, float natural) {
        return naturalHeight() ? natural : height * screenH;
    }

    /** The line the settings file holds: field, anchor, x, y, w, h, align, locked, shown. */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append(field).append('|').append(anchor).append('|')
                .append(x).append('|').append(y).append('|')
                .append(width).append('|').append(height).append('|')
                .append(align).append('|').append(locked ? 1 : 0).append('|')
                .append(shown ? 1 : 0).append('|').append(edge);
        return out.toString();
    }

    /**
     * A box read back from {@link #text()}, repaired where the text is wrong.
     *
     * @return the box, or null when the line names no field — which is not a damaged box but no box
     */
    public static LayoutBox parse(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        String field = parts[0].trim();
        if (field.isEmpty()) {
            return null;
        }
        return new LayoutBox(field,
                intAt(parts, 1, Anchor.MIDDLE_CENTRE),
                floatAt(parts, 2, 0f),
                floatAt(parts, 3, 0f),
                floatAt(parts, 4, NATURAL),
                floatAt(parts, 5, NATURAL),
                intAt(parts, 6, Anchor.MIDDLE_CENTRE),
                intAt(parts, 7, 0) != 0,
                intAt(parts, 8, 1) != 0,
                intAt(parts, 9, Strips.NONE));
    }

    private static int intAt(String[] parts, int index, int fallback) {
        if (index >= parts.length) {
            return fallback;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float floatAt(String[] parts, int index, float fallback) {
        if (index >= parts.length) {
            return fallback;
        }
        try {
            return Float.parseFloat(parts[index].trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** An offset is a fraction of the screen, and a box more than a screen away is a mistake. */
    private static float clampOffset(float value) {
        if (value != value) {                       // NaN, which comparisons would let through
            return 0f;
        }
        return value < -1f ? -1f : value > 1f ? 1f : value;
    }

    /** A size is a fraction of the screen, or natural. Zero and less are not sizes. */
    private static float clampSize(float value) {
        if (value != value) {
            return NATURAL;
        }
        if (value == NATURAL) {
            return NATURAL;
        }
        if (value <= 0f) {
            return NATURAL;
        }
        return value > 1f ? 1f : value;
    }
}
