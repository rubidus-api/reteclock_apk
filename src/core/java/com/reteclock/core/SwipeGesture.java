package com.reteclock.core;

/**
 * What counts as a sideways swipe on the clock (issue #70, R144). Pixels in, a step out.
 *
 * <p>A swipe goes clearly across: at least the larger of 64 dp and a sixth of the screen's width,
 * and at least twice as far across as up or down, so a tap, a long press or a hand brushing the
 * screen on its way to the bedside lamp is not one. The touch is claimed for a swipe sooner — 16 dp
 * across and twice as far across as down — so the tap and the long press under it can be called off
 * before they happen.
 */
public final class SwipeGesture {

    private static final float FAR_DP = 64f;
    private static final float CLAIM_DP = 16f;

    private SwipeGesture() {
    }

    /** How far across a swipe must go, in pixels. */
    public static float distance(float widthPx, float density) {
        return Math.max(FAR_DP * density, widthPx / 6f);
    }

    /** 1 for the next layout (finger right to left), -1 for the one before, 0 for no swipe. */
    public static int step(float dx, float dy, float widthPx, float density) {
        float across = Math.abs(dx);
        if (across < distance(widthPx, density) || across < 2f * Math.abs(dy)) {
            return 0;
        }
        return dx < 0 ? 1 : -1;
    }

    /** Whether a touch that has moved this far is to be taken as a swipe from now on. */
    public static boolean claims(float dx, float dy, float density) {
        float across = Math.abs(dx);
        return across > CLAIM_DP * density && across > 2f * Math.abs(dy);
    }
}
