package com.reteclock.core;

/**
 * The colon between the hour and the minute, blinking once a second (issue #32).
 *
 * What a table clock does: the colon is lit for half a second and dark for the other half, and it
 * has done so since digital clocks had a second to spare. The reason it reads as a heartbeat rather
 * than as a fault is that it is **locked to the second itself** — the colon goes dark exactly at the
 * half second and lights exactly as the second turns, whatever moment the clock was started at. So
 * this is arithmetic on the instant and holds no state at all.
 *
 * <p>Only where there is a colon. A wide screen writes the time on one line, `12:34`, and that line
 * has one; a tall screen stacks the hour over the minute and has none, so there is nothing to blink
 * and nothing happens. That is the option as it was asked for.
 */
public final class ColonBlink {

    /** One blink: lit, then dark, then lit again as the next second begins. */
    public static final long PERIOD_MS = 1000L;
    /** How much of that the colon is lit for — half, which is what the clocks being imitated do. */
    public static final long LIT_MS = 500L;

    private ColonBlink() {
    }

    /** Whether the colon is lit at this instant. */
    public static boolean showsAt(long epochMillis) {
        return within(epochMillis) < LIT_MS;
    }

    /**
     * How long until it next changes — which is when the clock has to draw again.
     *
     * Never zero: a redraw scheduled for now is a loop with no gap in it. Asked exactly on a change,
     * it answers the whole of the next half.
     */
    public static long millisToNextChange(long epochMillis) {
        long into = within(epochMillis);
        return into < LIT_MS ? LIT_MS - into : PERIOD_MS - into;
    }

    /**
     * How far into the current second an instant is.
     *
     * Written out rather than left to {@code %}, which keeps the sign of the dividend: an instant
     * before 1970 would otherwise land outside the period and read as lit when it is dark.
     */
    private static long within(long epochMillis) {
        long rest = epochMillis % PERIOD_MS;
        return rest < 0 ? rest + PERIOD_MS : rest;
    }

    /**
     * The same string with the trailing punctuation taken off, for the dark half of the blink.
     *
     * The colon is drawn on the end of the hour — punctuation belongs to what it follows — so
     * hiding it is removing the last character, and only when that character really is the
     * separator. Everything else is handed back untouched, including a colon in the middle of a
     * string, which is somebody else's colon.
     */
    public static String without(String piece, String punctuation) {
        if (piece == null || punctuation == null || punctuation.isEmpty()) {
            return piece;
        }
        return piece.endsWith(punctuation)
                ? piece.substring(0, piece.length() - punctuation.length())
                : piece;
    }
}
