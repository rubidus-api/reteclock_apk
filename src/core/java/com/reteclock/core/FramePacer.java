package com.reteclock.core;

/**
 * How fast to ask for the next frame of a moving picture, given what the last few cost.
 *
 * The rule is that the drawing may have about a third of the interval and no more. A device that
 * can draw a frame in 5 ms plays at the full rate; one that needs 60 ms plays at five frames a
 * second, and in both cases two thirds of the time is left for the clock and for answering a touch.
 *
 * This replaces standing the animation down altogether, which was too blunt: a picture that costs
 * more than one frame's worth of time is not a picture that must stop moving, it is one that should
 * move more slowly. Only something truly hopeless — frame after frame costing more than the slowest
 * pace allows — is given up on, and then the picture freezes rather than the clock.
 */
public final class FramePacer {

    /** The quickest the animation is ever asked for: 25 frames a second. */
    public static final long FASTEST_MS = 40L;

    /** The slowest it will go before being given up on: two frames a second. */
    public static final long SLOWEST_MS = 500L;

    /** How much of the interval the drawing may take: one third. */
    public static final long HEADROOM = 3L;

    /**
     * A frame costing more than this is beyond pacing: it takes longer than the slowest interval
     * on offer, so there is no rate at which the picture moves and the clock still answers.
     */
    public static final long HOPELESS_MS = SLOWEST_MS;

    /** How many hopeless frames in a row before the animation is abandoned. */
    public static final int HOPELESS_FRAMES = 4;

    /** How many recent frames the pace is taken from. */
    public static final int WINDOW = 8;

    private final long[] costs = new long[WINDOW];
    private int at;
    private int hopeless;
    private boolean givenUp;

    /** Records what one frame cost to draw. */
    public void sample(long costMs) {
        if (givenUp) {
            return;
        }
        costs[at] = Math.max(costMs, 0L);
        at = (at + 1) % WINDOW;
        if (costMs > HOPELESS_MS) {
            hopeless++;
            if (hopeless >= HOPELESS_FRAMES) {
                givenUp = true;
            }
        } else {
            hopeless = 0;
        }
    }

    /**
     * How long to wait before the next frame: three times the worst of the recent ones, never
     * quicker than {@link #FASTEST_MS} and never slower than {@link #SLOWEST_MS}. The worst rather
     * than the average, because a pace set by the average is one the slow frames keep overrunning.
     */
    public long delayMs() {
        long worst = 0L;
        for (int i = 0; i < WINDOW; i++) {
            if (costs[i] > worst) {
                worst = costs[i];
            }
        }
        long wanted = worst * HEADROOM;
        if (wanted < FASTEST_MS) {
            return FASTEST_MS;
        }
        return wanted > SLOWEST_MS ? SLOWEST_MS : wanted;
    }

    /** Whether this device cannot play the picture at any pace worth having. */
    public boolean givenUp() {
        return givenUp;
    }
}
