package com.reteclock.core.layout;

/**
 * The two things that take a whole edge: the timer's strip and the saying (RFC-0005, D9).
 *
 * Neither is a box like the others. The strip has controls to touch, and the saying is a sentence
 * that wants a run rather than a rectangle — half a screen wide it becomes three words a line. So
 * each is given an edge and takes the whole of it, and what is left over is where every other box
 * lives.
 *
 * Three rules settle every case:
 *
 * 1. **They may not share an edge.** A second strip asking for an edge that is taken is moved to the
 *    opposite one rather than halving it.
 * 1a. **The saying only runs along the top or the bottom.** Down a side it would be a column two
 *    words wide. The timer may take any of the four: it is a bar and a few numbers, not a sentence.
 * 2. **The timer is placed first**, at the full length of its edge. It is the one with controls, and
 *    a control that moves depending on what else is switched on is a control people press by
 *    mistake.
 * 3. **The saying takes the longest run left on its own edge**: where the two edges meet, it starts
 *    after the timer's thickness rather than under it.
 *
 * The content rectangle is what remains. Boxes are placed against *that* rather than against the
 * screen, so switching the timer on moves the clock over instead of putting the strip on top of it.
 */
public final class Strips {

    /** Not shown at all. */
    public static final int NONE = -1;
    public static final int TOP = 0;
    public static final int BOTTOM = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    /**
     * The least of the screen that must be left for everything else.
     *
     * A strip is a strip: at some thickness it stops being an edge and becomes the screen. Rather
     * than refuse a number, it is trimmed to this — the clock is what the app is for.
     */
    private static final float MOST_A_STRIP_MAY_TAKE = 0.5f;

    private final float[] timer;
    private final float[] saying;
    private final float[] content;
    private final int timerEdge;
    private final int sayingEdge;

    private Strips(float[] timer, float[] saying, float[] content, int timerEdge, int sayingEdge) {
        this.timer = timer;
        this.saying = saying;
        this.content = content;
        this.timerEdge = timerEdge;
        this.sayingEdge = sayingEdge;
    }

    /** Where the timer's strip goes, or null when it is not shown. Left, top, width, height. */
    public float[] timer() {
        return copy(timer);
    }

    /** Where the saying goes, or null when it is not shown. */
    public float[] saying() {
        return copy(saying);
    }

    /** What is left for everything else. Never empty. */
    public float[] content() {
        return copy(content);
    }

    /** The edge each ended up on, which is not always the edge that was asked for. */
    public int timerEdge() {
        return timerEdge;
    }

    public int sayingEdge() {
        return sayingEdge;
    }

    /**
     * Works out both strips and what is left.
     *
     * @param timerEdge      one of {@link #TOP}, {@link #BOTTOM}, {@link #LEFT}, {@link #RIGHT},
     *                       or {@link #NONE}
     * @param timerThickness how deep the strip is, in pixels
     * @param sayingEdge     likewise for the saying; moved to the opposite edge if it clashes
     */
    public static Strips of(int screenW, int screenH, int timerEdge, float timerThickness,
            int sayingEdge, float sayingThickness) {
        float[] content = {0f, 0f, screenW, screenH};

        int firstEdge = valid(timerEdge) ? timerEdge : NONE;
        float[] first = null;
        if (firstEdge != NONE) {
            float thickness = trim(firstEdge, timerThickness, screenW, screenH);
            first = cut(content, firstEdge, thickness);
        }

        // The saying runs along the top or the bottom and nowhere else: a sentence down the side of
        // a screen is a column two words wide, which is not reading. Asked for a side it takes the
        // foot — and the top if the timer is already at the foot, since the one rule it cannot
        // break is sharing an edge.
        int secondEdge = valid(sayingEdge) ? sayingEdge : NONE;
        if (secondEdge == LEFT || secondEdge == RIGHT) {
            secondEdge = BOTTOM;
        }
        if (secondEdge != NONE && secondEdge == firstEdge) {
            secondEdge = opposite(secondEdge);
        }
        float[] second = null;
        if (secondEdge != NONE) {
            float thickness = trim(secondEdge, sayingThickness, screenW, screenH);
            second = cut(content, secondEdge, thickness);
        }
        return new Strips(first, second, content, firstEdge, secondEdge);
    }

    /**
     * Takes a strip off one side of what is left, and answers the strip.
     *
     * The rectangle handed in is shrunk in place, which is what makes the second strip start after
     * the first: the saying is cut from what the timer left, not from the screen.
     */
    private static float[] cut(float[] left, int edge, float thickness) {
        float depth = edge == TOP || edge == BOTTOM
                ? Math.min(thickness, left[3] - 1f)
                : Math.min(thickness, left[2] - 1f);
        if (depth <= 0f) {
            return null;
        }
        float[] strip;
        if (edge == TOP) {
            strip = new float[] {left[0], left[1], left[2], depth};
            left[1] += depth;
            left[3] -= depth;
        } else if (edge == BOTTOM) {
            strip = new float[] {left[0], left[1] + left[3] - depth, left[2], depth};
            left[3] -= depth;
        } else if (edge == LEFT) {
            strip = new float[] {left[0], left[1], depth, left[3]};
            left[0] += depth;
            left[2] -= depth;
        } else {
            strip = new float[] {left[0] + left[2] - depth, left[1], depth, left[3]};
            left[2] -= depth;
        }
        return strip;
    }

    private static float trim(int edge, float thickness, int screenW, int screenH) {
        float most = (edge == TOP || edge == BOTTOM ? screenH : screenW) * MOST_A_STRIP_MAY_TAKE;
        return Math.max(0f, Math.min(thickness, most));
    }

    private static boolean valid(int edge) {
        return edge >= TOP && edge <= RIGHT;
    }

    private static int opposite(int edge) {
        return edge == TOP ? BOTTOM : edge == BOTTOM ? TOP : edge == LEFT ? RIGHT : LEFT;
    }

    private static float[] copy(float[] rect) {
        return rect == null ? null : new float[] {rect[0], rect[1], rect[2], rect[3]};
    }
}
