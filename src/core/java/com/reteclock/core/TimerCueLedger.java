package com.reteclock.core;

/**
 * Up to where a run's cues have been played (R130, R145): kept by the one service that plays them,
 * and stored beside the run so a service started again carries on from it.
 *
 * <p>The mark is a moment of the run — its elapsed time — not of the clock, so a pause and a resume,
 * which move the run's origin, leave it where it was. The run is read again every time a screen
 * writes it down, and each reading opens its window at the mark: nothing plays twice, and an older
 * moment offered later never moves the mark back. That was the count-in heard twice: the clock's
 * pause and the screen-off broadcast both handed the run over, the second with the strip's older
 * moment.
 *
 * <p>A run is told apart by its preset and the moment it was started; a run not seen before opens
 * at its count-in. Neither opens further back than a moment ago ({@link #LATE_MS}) or a minute
 * ago ({@link #STALE_MS}), so a service that was held back plays what is due now, not a burst of
 * what it missed. Immutable.
 */
public final class TimerCueLedger {

    /** How far back a run first met may open: the start of the service, give or take. */
    public static final long LATE_MS = 2_000L;
    /** How far back a known run may open after a silence: a late cue, not an old one. */
    public static final long STALE_MS = 60_000L;

    /** Nothing played yet. */
    public static final TimerCueLedger NONE = new TimerCueLedger("", 0L);

    private final String key;
    private final long mark;

    public TimerCueLedger(String key, long mark) {
        this.key = key == null ? "" : key;
        this.mark = mark;
    }

    /** What tells one run from another: its preset and when it was started by the wall clock. */
    public static String keyOf(String presetIdentity, long startedEpochMs) {
        return (presetIdentity == null ? "" : presetIdentity) + "@" + startedEpochMs;
    }

    public String key() {
        return key;
    }

    /** The elapsed time of the run up to which its cues were played. */
    public long mark() {
        return mark;
    }

    /** Whether this is the ledger of that run. */
    public boolean isOf(String runKey) {
        return !key.isEmpty() && key.equals(runKey);
    }

    /** Where, on the clock the run is asked about, the window ending at {@code nowMs} opens. */
    public long windowFrom(String runKey, TimerRun run, long nowMs) {
        long elapsed = run.rawElapsedAt(nowMs);
        long from;
        if (isOf(runKey)) {
            from = Math.max(mark, elapsed - STALE_MS);
        } else {
            from = Math.max(-TimerCues.LEAD_IN_MS - 1L, elapsed - LATE_MS);
        }
        return nowMs - Math.max(0L, elapsed - from);
    }

    /** After the cues up to {@code nowMs} were played: the mark there, never further back. */
    public TimerCueLedger playedTo(String runKey, TimerRun run, long nowMs) {
        long elapsed = run.rawElapsedAt(nowMs);
        if (isOf(runKey)) {
            return elapsed <= mark ? this : new TimerCueLedger(key, elapsed);
        }
        return new TimerCueLedger(runKey, elapsed);
    }
}
