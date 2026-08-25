package com.reteclock.core.layout;

/**
 * What a finger has taken hold of on the editor's canvas, and what happens as it moves (RFC-0005,
 * phase 4, R93).
 *
 * The interaction the owner asked for is three gestures: **a corner resizes**, **the inside moves**,
 * and **a long press opens the numbers**. The first two are arithmetic and live here, where they can
 * be tested; the third is a dialog and needs none. What is left above this is drawing, which is the
 * part that cannot be tested on a JVM — so it is the part that should be smallest.
 *
 * Rectangles are {@code {left, top, width, height}} in the pixels of whatever is being edited, and
 * nothing here mutates what it is given: the editor keeps the previous list to undo with.
 */
public final class Grab {

    /** Nothing under the finger. */
    public static final int NONE = -1;
    /** The body of the box: the whole thing moves. */
    public static final int INSIDE = 0;
    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_LEFT = 3;
    public static final int BOTTOM_RIGHT = 4;

    private Grab() {
    }

    /**
     * What is under this point.
     *
     * A corner reaches a little outside the box as well as inside it: a handle is a target for a
     * fingertip, not a mathematical point. On a box small enough for the four handles to swallow
     * it, the middle still answers {@link #INSIDE} — a box that cannot be moved is a box that
     * cannot be rescued from wherever it is.
     *
     * @param reach how far from a corner still counts as that corner, in the same pixels
     */
    public static int at(float[] box, float x, float y, float reach) {
        float left = box[0];
        float top = box[1];
        float right = box[0] + box[2];
        float bottom = box[1] + box[3];

        // The handle may not eat more than a third of the box, so the middle is always grabbable.
        float grip = Math.min(reach, Math.min(box[2], box[3]) / 3f);
        if (near(x, left, grip) && near(y, top, grip)) {
            return TOP_LEFT;
        }
        if (near(x, right, grip) && near(y, top, grip)) {
            return TOP_RIGHT;
        }
        if (near(x, left, grip) && near(y, bottom, grip)) {
            return BOTTOM_LEFT;
        }
        if (near(x, right, grip) && near(y, bottom, grip)) {
            return BOTTOM_RIGHT;
        }
        if (x >= left && x <= right && y >= top && y <= bottom) {
            return INSIDE;
        }
        return NONE;
    }

    /**
     * The box after a drag of {@code dx, dy} on whatever was grabbed.
     *
     * Two rules that are not about geometry but about not losing things:
     *
     * - **A box cannot be turned inside out.** Pulling a corner past the opposite one stops at
     *   {@code least} rather than flipping: a negative width is a number every arithmetic below
     *   this would go on to divide by.
     * - **Nothing leaves the screen.** What cannot be seen cannot be grabbed back, and the editor
     *   would have lost the box for good.
     */
    public static float[] apply(float[] box, int grabbed, float dx, float dy,
            float screenW, float screenH, float least) {
        float[] out = {box[0], box[1], box[2], box[3]};
        if (grabbed == NONE) {
            return out;
        }
        if (grabbed == INSIDE) {
            out[0] = clamp(box[0] + dx, 0f, screenW - box[2]);
            out[1] = clamp(box[1] + dy, 0f, screenH - box[3]);
            return out;
        }

        float left = box[0];
        float top = box[1];
        float right = box[0] + box[2];
        float bottom = box[1] + box[3];

        if (grabbed == TOP_LEFT || grabbed == BOTTOM_LEFT) {
            left = clamp(left + dx, 0f, right - least);
        } else {
            right = clamp(right + dx, left + least, screenW);
        }
        if (grabbed == TOP_LEFT || grabbed == TOP_RIGHT) {
            top = clamp(top + dy, 0f, bottom - least);
        } else {
            bottom = clamp(bottom + dy, top + least, screenH);
        }
        out[0] = left;
        out[1] = top;
        out[2] = right - left;
        out[3] = bottom - top;
        return out;
    }

    private static boolean near(float value, float target, float reach) {
        return value >= target - reach && value <= target + reach;
    }

    private static float clamp(float value, float least, float most) {
        if (most < least) {
            return least;
        }
        return value < least ? least : value > most ? most : value;
    }
}
