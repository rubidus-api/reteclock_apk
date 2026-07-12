package com.reteclock.core;

/**
 * OLED burn-in protection.
 *
 * The whole drawing is translated by a small offset that walks a closed path: the angle advances
 * one step of a full turn each minute while the radius cycles through three values, so the offsets
 * spread over a small disc instead of retracing a single ring. Every position in a cycle is
 * distinct, consecutive positions are close together, and the path returns to its start after
 * {@link #STEPS} steps.
 *
 * Pure Java: no android.* imports.
 */
public final class BurnInShift {

    /** How long one position is held, in milliseconds. */
    public static final long STEP_MS = 60000L;

    /** Number of distinct positions in one full cycle. */
    public static final int STEPS = 24;

    /** Shift amplitude as a fraction of the shorter screen edge. */
    private static final float AMPLITUDE_FRACTION = 0.03f;

    private static final int MIN_SHIFT_PX = 4;
    private static final int MAX_SHIFT_PX = 32;

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
     * Radius for a step: full, then 70%, then 40%, repeating.
     *
     * STEPS is a multiple of the radius cycle length, so the whole path closes cleanly.
     */
    private static double radius(int stepIndex, int maxShiftPx) {
        return maxShiftPx * (1.0 - 0.3 * (stepIndex % 3));
    }
}
