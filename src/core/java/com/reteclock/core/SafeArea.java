package com.reteclock.core;

/**
 * How much of the screen a television may be eating.
 *
 * Older sets overscan: the panel shows less than the signal carries, and up to a twentieth of each
 * edge falls outside the tube. A clock does not care much — big digits in the middle survive — but
 * the saying, the date line and the timer's strip live *at* the edges, and the first and last
 * characters of a saying are exactly what goes missing.
 *
 * So the margin is offered, and it is off unless it is asked for. Sets made in the last ten years
 * do not overscan, and a margin on one of those is a smaller clock for no reason: nobody can tell
 * from inside the app which kind of set it is on, and guessing wrong is worse either way than
 * letting the person who can see the screen decide (RFC-0009, Q2).
 *
 * Pure arithmetic, so the number is testable without a television.
 */
public final class SafeArea {

    /**
     * The share of each edge kept clear when the margin is on: a twentieth.
     *
     * The figure television design guidance uses. It is per edge, so a tenth of the width and a
     * tenth of the height are given up in total.
     */
    public static final float SHARE = 0.05f;

    private SafeArea() {
    }

    /**
     * The margin in pixels for a screen of this size, or zero when it is not wanted.
     *
     * The same number on every edge — a square margin on a rectangular screen — because overscan is
     * a property of the tube rather than of the picture, and a margin that differed by axis would
     * put the date line and the saying at different distances from an edge that eats the same
     * amount of both.
     */
    public static int marginPx(int width, int height, boolean wanted) {
        if (!wanted || width <= 0 || height <= 0) {
            return 0;
        }
        int shorter = Math.min(width, height);
        return Math.round(shorter * SHARE);
    }
}
