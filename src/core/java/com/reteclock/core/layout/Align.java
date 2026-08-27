package com.reteclock.core.layout;

/**
 * Aligning and distributing a selection of boxes, the way a drawing program does it (RFC-0005, R90).
 *
 * These operate on a *selection*: aligning left gives every box in it the smallest left edge among
 * them. That is a different question from where a box's own contents sit inside its rectangle, which
 * is the box's alignment ({@link LayoutBox#align}) — conflating the two is the usual way this
 * feature goes wrong, so they are two words in this engine and never one.
 *
 * A rectangle is {@code {left, top, width, height}}. Every operation answers new rectangles and
 * leaves the ones it was given alone, so the editor's undo is the list it had before. One box is a
 * selection too, and so is none: aligning a single object does nothing visible in any drawing
 * program, and nothing here falls over on an empty selection.
 */
public final class Align {

    private Align() {
    }

    /** Every box gets the leftmost left edge in the selection. */
    public static float[][] left(float[][] boxes) {
        float[][] out = copy(boxes);
        if (out.length < 2) {
            return out;
        }
        float edge = out[0][0];
        for (float[] r : out) {
            edge = Math.min(edge, r[0]);
        }
        for (float[] r : out) {
            r[0] = edge;
        }
        return out;
    }

    /** Every box gets the rightmost right edge, so they end together rather than start together. */
    public static float[][] right(float[][] boxes) {
        float[][] out = copy(boxes);
        if (out.length < 2) {
            return out;
        }
        float edge = out[0][0] + out[0][2];
        for (float[] r : out) {
            edge = Math.max(edge, r[0] + r[2]);
        }
        for (float[] r : out) {
            r[0] = edge - r[2];
        }
        return out;
    }

    /** Their middles line up, on the middle of what the selection spans. */
    public static float[][] centreX(float[][] boxes) {
        float[][] out = copy(boxes);
        if (out.length < 2) {
            return out;
        }
        float middle = (min(out, 0) + max(out, 0)) / 2f;
        for (float[] r : out) {
            r[0] = middle - r[2] / 2f;
        }
        return out;
    }

    /** The same rule turned sideways. */
    public static float[][] top(float[][] boxes) {
        float[][] out = copy(boxes);
        if (out.length < 2) {
            return out;
        }
        float edge = out[0][1];
        for (float[] r : out) {
            edge = Math.min(edge, r[1]);
        }
        for (float[] r : out) {
            r[1] = edge;
        }
        return out;
    }

    public static float[][] bottom(float[][] boxes) {
        float[][] out = copy(boxes);
        if (out.length < 2) {
            return out;
        }
        float edge = out[0][1] + out[0][3];
        for (float[] r : out) {
            edge = Math.max(edge, r[1] + r[3]);
        }
        for (float[] r : out) {
            r[1] = edge - r[3];
        }
        return out;
    }

    public static float[][] middleY(float[][] boxes) {
        float[][] out = copy(boxes);
        if (out.length < 2) {
            return out;
        }
        float middle = (min(out, 1) + max(out, 1)) / 2f;
        for (float[] r : out) {
            r[1] = middle - r[3] / 2f;
        }
        return out;
    }

    /**
     * Equal gaps between them, the two ends staying where they are.
     *
     * Sorted by where they sit rather than by the order they were selected in: a person who rubber
     * bands three boxes and asks for even spacing means the ones on the screen, not the ones in the
     * list.
     */
    public static float[][] distributeX(float[][] boxes) {
        return distribute(boxes, 0);
    }

    public static float[][] distributeY(float[][] boxes) {
        return distribute(boxes, 1);
    }

    private static float[][] distribute(float[][] boxes, int axis) {
        float[][] out = copy(boxes);
        if (out.length < 3) {
            return out;                            // two boxes are already evenly spaced
        }
        int size = axis + 2;
        int[] order = orderAlong(out, axis);

        // The two edges that stay put are the outermost ones — the smallest start and the largest
        // end — not the boxes that happen to start first and last. With boxes of different sizes the
        // one that starts last need not be the one that reaches furthest, and a distribute that
        // moved the far edge inward would shrink the arrangement a little every time it was pressed.
        float first = min(out, axis);
        float lastEnd = max(out, axis);
        float span = lastEnd - first;
        float used = 0f;
        for (float[] r : out) {
            used += r[size];
        }
        float gap = (span - used) / (out.length - 1);

        float cursor = first;
        for (int i = 0; i < order.length; i++) {
            float[] r = out[order[i]];
            r[axis] = cursor;
            cursor += r[size] + gap;
        }
        return out;
    }

    /** Which box comes first along this axis, second, and so on. */
    private static int[] orderAlong(float[][] boxes, int axis) {
        int[] order = new int[boxes.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        // Insertion sort: a selection is a handful of boxes, and this keeps the class dependency-free.
        for (int i = 1; i < order.length; i++) {
            int hold = order[i];
            int j = i - 1;
            while (j >= 0 && boxes[order[j]][axis] > boxes[hold][axis]) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = hold;
        }
        return order;
    }

    /** The largest width in the selection, for all of them (D5). Nothing moves. */
    public static float[][] sameWidth(float[][] boxes) {
        float[][] out = copy(boxes);
        float widest = 0f;
        for (float[] r : out) {
            widest = Math.max(widest, r[2]);
        }
        for (float[] r : out) {
            r[2] = widest;
        }
        return out;
    }

    public static float[][] sameHeight(float[][] boxes) {
        float[][] out = copy(boxes);
        float tallest = 0f;
        for (float[] r : out) {
            tallest = Math.max(tallest, r[3]);
        }
        for (float[] r : out) {
            r[3] = tallest;
        }
        return out;
    }

    public static float[][] sameSize(float[][] boxes) {
        return sameHeight(sameWidth(boxes));
    }

    /** Centred on the screen rather than on each other — the selection's own extent is ignored. */
    public static float[][] centreOnScreenX(float[][] boxes, float screenW) {
        float[][] out = copy(boxes);
        for (float[] r : out) {
            r[0] = (screenW - r[2]) / 2f;
        }
        return out;
    }

    public static float[][] centreOnScreenY(float[][] boxes, float screenH) {
        float[][] out = copy(boxes);
        for (float[] r : out) {
            r[1] = (screenH - r[3]) / 2f;
        }
        return out;
    }

    /** Everything moves by the same step. */
    public static float[][] nudge(float[][] boxes, float dx, float dy) {
        float[][] out = copy(boxes);
        for (float[] r : out) {
            r[0] += dx;
            r[1] += dy;
        }
        return out;
    }

    private static float min(float[][] boxes, int axis) {
        float least = boxes[0][axis];
        for (float[] r : boxes) {
            least = Math.min(least, r[axis]);
        }
        return least;
    }

    private static float max(float[][] boxes, int axis) {
        int size = axis + 2;
        float most = boxes[0][axis] + boxes[0][size];
        for (float[] r : boxes) {
            most = Math.max(most, r[axis] + r[size]);
        }
        return most;
    }

    private static float[][] copy(float[][] boxes) {
        float[][] out = new float[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            out[i] = new float[] {boxes[i][0], boxes[i][1], boxes[i][2], boxes[i][3]};
        }
        return out;
    }
}
