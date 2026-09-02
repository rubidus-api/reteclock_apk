package com.reteclock.core;

/**
 * The test a quality step has to pass before it may be switched on (RFC-0011).
 *
 * The rule the owner asked for: **a step is not saved when it is chosen, it is saved when it has
 * been survived.** The screen writes a mark, does the hard thing for real — bakes at the new step
 * and plays it — and writes the setting only if it comes out the other side. If the app dies or
 * hangs in the middle, the only thing on disc is the mark, and the next start reads it as "this
 * phone did not survive that step" and refuses it.
 *
 * The worth of that is one sentence: **the worst a failed trial can do is fail.** The clock is never
 * asked to use a step that has not already been played through once, so a step that kills the app
 * cannot leave a clock that will not start.
 *
 * This class is the arithmetic — what counts as surviving — so the rule can be tested without a
 * phone. The mark, the baking and the playing belong to the Android layer.
 */
public final class ImageTrial {

    /** How long the trial plays before it is willing to answer, in milliseconds. */
    public static final long PLAY_MS = 6000L;

    /** Longest a bake may take before the step is refused, in milliseconds. */
    public static final long BAKE_LIMIT_MS = 90000L;

    /**
     * How long a frame may take, on the median and at worst.
     *
     * The median is what the clock feels like; the worst is what makes it stutter. Thirty-three
     * milliseconds is a frame at 30 a second, and 120 is the point at which a dropped frame is
     * something a person sees rather than something an instrument finds.
     */
    public static final long MEDIAN_LIMIT_MS = 33L;
    public static final long WORST_LIMIT_MS = 120L;

    /** What one trial measured. */
    public static final class Measured {
        public final long bakeMs;
        public final long medianFrameMs;
        public final long worstFrameMs;
        public final int framesDrawn;
        public final long packBytes;

        public Measured(long bakeMs, long medianFrameMs, long worstFrameMs, int framesDrawn,
                long packBytes) {
            this.bakeMs = bakeMs;
            this.medianFrameMs = medianFrameMs;
            this.worstFrameMs = worstFrameMs;
            this.framesDrawn = framesDrawn;
            this.packBytes = packBytes;
        }
    }

    /** How many frames a trial must actually have drawn before its numbers mean anything. */
    public static final int LEAST_FRAMES = 30;

    private ImageTrial() {
    }

    /**
     * Whether these measurements are good enough to switch the step on.
     *
     * A trial that drew almost nothing has not measured anything, and is refused rather than
     * believed: a phone that managed four frames in six seconds has told us the answer, but not in
     * the numbers this looks at.
     */
    public static boolean passed(Measured measured, int step, long budgetBytes) {
        if (measured == null || measured.framesDrawn < LEAST_FRAMES) {
            return false;
        }
        if (measured.bakeMs > BAKE_LIMIT_MS) {
            return false;
        }
        if (measured.medianFrameMs > MEDIAN_LIMIT_MS || measured.worstFrameMs > WORST_LIMIT_MS) {
            return false;
        }
        return measured.packBytes <= budgetBytes;
    }

    /**
     * Why it did not pass, in the plainest words there are, or null when it did.
     *
     * The screen shows the numbers as well; this is the sentence beside them. It names one reason —
     * the first that applies — because a list of four is a list nobody reads.
     */
    public static String complaint(Measured measured, int step, long budgetBytes) {
        if (measured == null || measured.framesDrawn < ImageTrial.LEAST_FRAMES) {
            return "The test did not manage to draw enough to judge by.";
        }
        if (measured.bakeMs > BAKE_LIMIT_MS) {
            return "Preparing the picture took too long on this phone.";
        }
        if (measured.medianFrameMs > MEDIAN_LIMIT_MS) {
            return "Playing it was too slow to keep a clock smooth.";
        }
        if (measured.worstFrameMs > WORST_LIMIT_MS) {
            return "It played, but with pauses long enough to see.";
        }
        if (measured.packBytes > budgetBytes) {
            return "It would take more room than this step allows.";
        }
        return null;
    }

    /**
     * The median of what was measured, from a list that need not be sorted.
     *
     * Here rather than in the screen because it is arithmetic, and because a median taken over an
     * empty list has to answer something rather than throw in the middle of a test whose whole
     * purpose is to survive.
     */
    public static long median(long[] values, int count) {
        if (values == null || count <= 0) {
            return 0L;
        }
        long[] sorted = new long[Math.min(count, values.length)];
        System.arraycopy(values, 0, sorted, 0, sorted.length);
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    public static long worst(long[] values, int count) {
        long worst = 0L;
        for (int i = 0; i < count && i < values.length; i++) {
            worst = Math.max(worst, values[i]);
        }
        return worst;
    }
}
