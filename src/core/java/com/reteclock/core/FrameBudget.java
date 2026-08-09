package com.reteclock.core;

/**
 * Watches what a moving background costs and decides when to stop moving it.
 *
 * A GIF is drawn by scaling every frame onto the whole screen in software. On a modern phone that
 * is nothing; on the 2012 hardware this app is built for it can cost more than the interval between
 * frames, and once it does the UI thread never falls idle — the clock stops answering touches and
 * looks frozen. So the cost of each animated frame is fed in here, and when too many of the last
 * few frames were over budget the caller freezes the picture at the frame it reached.
 *
 * The decision is one-way for the run: a clock that alternated between moving and still, as the
 * measurements wandered around the budget, would be worse than one that simply settled.
 */
public final class FrameBudget {

    /**
     * What one animated frame may cost. Half of the 40 ms animation tick, so a device that stays
     * under it still has half of every interval left for touches, the tick and everything else.
     */
    public static final long BUDGET_MS = 20L;

    /** How many recent frames are looked at. */
    public static final int WINDOW = 12;

    /** How many of those must be over budget before the animation is stood down. */
    public static final int OVERRUNS = 6;

    /** The window, as a ring of over-budget flags. */
    private final boolean[] over = new boolean[WINDOW];
    private int at;
    private int count;
    private boolean overloaded;

    /** Records what one animated frame cost. */
    public void sample(long costMs) {
        if (overloaded) {
            return;
        }
        if (over[at]) {
            count--;
        }
        boolean bad = costMs > BUDGET_MS;
        over[at] = bad;
        if (bad) {
            count++;
        }
        at = (at + 1) % WINDOW;
        if (count >= OVERRUNS) {
            overloaded = true;
        }
    }

    /** Whether this device cannot afford to keep the picture moving. */
    public boolean overloaded() {
        return overloaded;
    }
}
