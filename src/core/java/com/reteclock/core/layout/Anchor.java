package com.reteclock.core.layout;

/**
 * The nine points a box can be measured from (RFC-0005, R90).
 *
 * An anchor names a point on the screen *and* the matching point on the box: anchor a box
 * bottom-right and its own bottom-right corner is what gets put there, so the box extends back into
 * the screen rather than off it. That is what makes an anchor useful — a box anchored to the bottom
 * of the screen stays at the bottom when the screen is a different size, and one anchored to the
 * middle stays in the middle.
 *
 * The offset runs **inward from the edge the anchor names**: on a right-anchored box, twenty means
 * twenty further left. On a centred axis there is no edge to run from, so the offset is a plain
 * displacement, positive to the right and downwards.
 *
 * Pure arithmetic on a screen of a given size — no android.*, no pixels-per-inch, nothing about what
 * a box contains. Everything else in the engine rests on this, so it is one small class with one
 * test file.
 */
public final class Anchor {

    /** Left, centre, right. */
    public static final int LEFT = 0;
    public static final int CENTRE = 1;
    public static final int RIGHT = 2;
    /** Top, middle, bottom. */
    public static final int TOP = 0;
    public static final int MIDDLE = 1;
    public static final int BOTTOM = 2;

    public static final int TOP_LEFT = 0;
    public static final int TOP_CENTRE = 1;
    public static final int TOP_RIGHT = 2;
    public static final int MIDDLE_LEFT = 3;
    public static final int MIDDLE_CENTRE = 4;
    public static final int MIDDLE_RIGHT = 5;
    public static final int BOTTOM_LEFT = 6;
    public static final int BOTTOM_CENTRE = 7;
    public static final int BOTTOM_RIGHT = 8;

    /** How many there are, so callers can walk them without naming all nine. */
    public static final int COUNT = 9;

    private Anchor() {
    }

    /** Which of {@link #LEFT}, {@link #CENTRE}, {@link #RIGHT} this anchor uses. */
    public static int horizontal(int anchor) {
        return clamp(anchor) % 3;
    }

    /** Which of {@link #TOP}, {@link #MIDDLE}, {@link #BOTTOM} this anchor uses. */
    public static int vertical(int anchor) {
        return clamp(anchor) / 3;
    }

    /** The anchor made of those two, for callers that hold the axes apart. */
    public static int of(int horizontal, int vertical) {
        int h = horizontal < LEFT ? LEFT : horizontal > RIGHT ? RIGHT : horizontal;
        int v = vertical < TOP ? TOP : vertical > BOTTOM ? BOTTOM : vertical;
        return v * 3 + h;
    }

    /**
     * Where a box of this size ends up: left, top, width, height.
     *
     * @param anchor   one of the nine
     * @param x        offset along the anchored horizontal edge, inward; a displacement when centred
     * @param y        the same vertically
     * @param width    the box's width, already decided
     * @param height   the box's height
     * @param screenW  the screen it is placed on
     * @param screenH  likewise
     */
    public static float[] rect(int anchor, float x, float y, float width, float height,
            float screenW, float screenH) {
        return new float[] {
            along(horizontal(anchor), x, width, screenW),
            along(vertical(anchor), y, height, screenH),
            width,
            height,
        };
    }

    /** One axis of {@link #rect}: the same rule for both, which is why there is one of it. */
    private static float along(int side, float offset, float size, float extent) {
        if (side == LEFT) {          // and TOP, which is the same number
            return offset;
        }
        if (side == RIGHT) {         // and BOTTOM: the offset runs back from the far edge
            return extent - size - offset;
        }
        return (extent - size) / 2f + offset;
    }

    /**
     * The horizontal offset that would put a box of this width at this left edge, under this anchor.
     *
     * The inverse of {@link #rect}, and the reason the editor can change a box's anchor without the
     * box moving: it reads the rectangle it has, and writes the offset the new anchor needs.
     */
    public static float offsetX(int anchor, float left, float width, float screenW) {
        return offset(horizontal(anchor), left, width, screenW);
    }

    /** The same vertically. */
    public static float offsetY(int anchor, float top, float height, float screenH) {
        return offset(vertical(anchor), top, height, screenH);
    }

    private static float offset(int side, float near, float size, float extent) {
        if (side == LEFT) {
            return near;
        }
        if (side == RIGHT) {
            return extent - size - near;
        }
        return near - (extent - size) / 2f;
    }

    private static int clamp(int anchor) {
        return anchor < 0 || anchor >= COUNT ? MIDDLE_CENTRE : anchor;
    }
}
