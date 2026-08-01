package com.reteclock.core;

/**
 * OLED burn-in protection.
 *
 * The whole drawing is translated by an offset that walks a closed path: the angle advances one
 * step of a full turn each time the position changes, while the radius cycles through three values,
 * so the offsets spread over a disc instead of retracing a single ring. Every position in a cycle is
 * distinct, consecutive positions are close together, and the path returns to its start after
 * {@link #STEPS} steps.
 *
 * The disc is deliberately wide and walked in many small steps: a wider spread wears the panel more
 * evenly, and a small step keeps the drift from being something you notice. The step lands on the
 * minute, when the displayed minute changes anyway, so the movement rides along with a change the
 * eye is already expecting.
 *
 * How far the drawing may move is bounded by the padding {@link ClockLayout} reserves, which is
 * derived from this amplitude, so content cannot be pushed off the screen.
 *
 * Pure Java: no android.* imports.
 */
public final class BurnInShift {

    /**
     * How long one position is held. One minute, because the minute digit changes then anyway, so
     * the drift rides along with a change the eye is already expecting.
     */
    public static final long STEP_MS = 60000L;

    /**
     * Number of distinct positions in one full cycle, so a cycle closes after STEPS * STEP_MS —
     * 144 minutes. Many positions is what keeps each minute's move tiny while the disc stays wide.
     */
    public static final int STEPS = 144;

    /** How many times the radius swells and shrinks over one cycle. */
    private static final int RADIAL_CYCLES = 3;

    /** The radius swings between these fractions of the maximum. */
    private static final double RADIUS_MIN = 0.4;
    private static final double RADIUS_MAX = 1.0;

    /** Shift amplitude as a fraction of the shorter screen edge. */
    public static final float AMPLITUDE_FRACTION = 0.06f;

    // Kept at one pixel: a larger floor could exceed the padding on a very small screen, and the
    // whole point of that padding is that the shift always fits inside it.
    private static final int MIN_SHIFT_PX = 1;
    private static final int MAX_SHIFT_PX = 96;

    private BurnInShift() {
    }

    /** Maximum offset in pixels for a screen of the given size. */
    public static int maxShiftPx(int widthPx, int heightPx) {
        int shorter = Math.min(widthPx, heightPx);
        int shift = Math.round(shorter * AMPLITUDE_FRACTION);
        if (shift < MIN_SHIFT_PX) {
            return MIN_SHIFT_PX;
        }
        if (shift > MAX_SHIFT_PX) {
            return MAX_SHIFT_PX;
        }
        return shift;
    }

    /** Zero-based position index for the given elapsed time. */
    public static int stepIndex(long elapsedMs) {
        long step = elapsedMs / STEP_MS;
        int index = (int) (step % STEPS);
        return index < 0 ? index + STEPS : index;
    }

    /** Horizontal offset in pixels, within [-maxShiftPx, +maxShiftPx]. */
    public static float offsetX(long elapsedMs, int maxShiftPx) {
        int index = stepIndex(elapsedMs);
        return (float) (radius(index, maxShiftPx) * Math.cos(angle(index)));
    }

    /** Vertical offset in pixels, within [-maxShiftPx, +maxShiftPx]. */
    public static float offsetY(long elapsedMs, int maxShiftPx) {
        int index = stepIndex(elapsedMs);
        return (float) (radius(index, maxShiftPx) * Math.sin(angle(index)));
    }

    private static double angle(int stepIndex) {
        return 2.0 * Math.PI * stepIndex / STEPS;
    }

    /**
     * Radius for a step: a smooth swell between 40% and 100%, three times around the cycle.
     *
     * It used to step between three fixed values, which moved the drawing by a third of the radius
     * from one position to the next. That was tolerable when a position lasted three minutes; with
     * a move every minute it would read as a lurch. Varying it smoothly keeps every move small,
     * and the path still closes because the swell divides the cycle evenly.
     */
    private static double radius(int stepIndex, int maxShiftPx) {
        double phase = 2.0 * Math.PI * RADIAL_CYCLES * stepIndex / STEPS;
        double unit = RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * (1.0 + Math.cos(phase)) / 2.0;
        return maxShiftPx * unit;
    }
}
