package com.reteclock.core;

/**
 * The rules that keep a bad picture from locking the owner out of their own clock.
 *
 * Two of them, both about time. The first: a start writes a mark, and clears it once the clock has
 * been drawing happily for a while. A start that finds the mark still there knows the run before it
 * never got that far — it was killed, or it hung — so this run leaves the images and the imported
 * fonts alone and says so. The second: every start draws plainly for a short grace period before it
 * touches an image at all, so there is always a window in which a long press is delivered and the
 * settings can be reached.
 *
 * Pure arithmetic, so the rules are unit tested on a JVM; the Android layer owns the stored mark.
 */
public final class SafeStart {

    /**
     * How long the clock draws plainly before the images begin.
     *
     * Long enough to press and hold; short enough that a working setup barely notices.
     */
    public static final long GRACE_MS = 3000L;

    /**
     * How long a run must survive before it is called healthy and the mark is cleared.
     *
     * Comfortably after {@link #GRACE_MS}, because it is the images that hang a clock: a mark
     * cleared before they were drawn would call a doomed run healthy and repeat it forever.
     */
    public static final long HEALTHY_MS = 12000L;

    private SafeStart() {
    }

    /** Whether this run leaves the images and the imported fonts alone. */
    public static boolean safeMode(boolean previousRunUnfinished) {
        return previousRunUnfinished;
    }

    /** Whether the grace period is over and the images may begin. */
    public static boolean imagesReady(long sinceStartMs) {
        return sinceStartMs >= GRACE_MS;
    }

    /** Whether the run has lasted long enough to clear its mark. */
    public static boolean healthy(long sinceStartMs) {
        return sinceStartMs >= HEALTHY_MS;
    }
}
