package com.reteclock.core;

/**
 * A preset, running: where it has got to at any moment, by the wall clock.
 *
 * Nothing here counts ticks. The run is asked "given that it is now this time, where are you", and
 * answers from arithmetic, so a late frame, a screen that was off, a rotation and a punctual tick
 * all get the same answer. That is what keeps the bar and the beeps agreeing with each other on
 * hardware that cannot promise to come back in forty milliseconds.
 *
 * Immutable: pausing and resuming return a new run. Times are elapsed milliseconds from any
 * monotonic clock — only differences matter.
 */
public final class TimerRun {

    private final TimerPreset preset;
    /** When the run began, already adjusted for whatever time it has spent paused. */
    private final long originMs;
    /** Where it stopped, or -1 while it is running. */
    private final long pausedAtMs;

    private TimerRun(TimerPreset preset, long originMs, long pausedAtMs) {
        this.preset = preset;
        this.originMs = originMs;
        this.pausedAtMs = pausedAtMs;
    }

    /** A preset just started. */
    public static TimerRun start(TimerPreset preset, long nowMs) {
        return new TimerRun(preset, nowMs, -1L);
    }

    public TimerPreset preset() {
        return preset;
    }

    /**
     * When it began, after however long it has spent paused — the number to write down when the
     * run has to be handed to another screen. See {@link TimerMemory}.
     */
    public long originMs() {
        return originMs;
    }

    /** When it was paused, or -1 while it is running. */
    public long pausedAtMs() {
        return pausedAtMs;
    }

    public boolean isPaused() {
        return pausedAtMs >= 0L;
    }

    /** Stopped where it stands; asking it the time again gives the same answer. */
    public TimerRun pausedAt(long nowMs) {
        return isPaused() ? this : new TimerRun(preset, originMs, nowMs);
    }

    /**
     * Going again from where it stopped. The origin moves forward by however long the pause
     * lasted, which is what makes "resume" mean "carry on" rather than "jump to now".
     */
    public TimerRun resumedAt(long nowMs) {
        if (!isPaused()) {
            return this;
        }
        return new TimerRun(preset, originMs + Math.max(0L, nowMs - pausedAtMs), -1L);
    }

    public long totalMs() {
        return preset.totalMs();
    }

    /**
     * How far into the preset it is, unclamped — negative before it began, past the total after it
     * ended. The cues need this: a window clamped at zero cannot tell "before the start" from "at
     * the start", and the preset's own opening cue falls exactly on zero.
     */
    public long rawElapsedAt(long nowMs) {
        return (isPaused() ? pausedAtMs : nowMs) - originMs;
    }

    /** How far into the whole preset it is; never past the end, never before the beginning. */
    public long elapsedAt(long nowMs) {
        long at = (isPaused() ? pausedAtMs : nowMs) - originMs;
        if (at < 0L) {
            return 0L;
        }
        long total = totalMs();
        return at > total ? total : at;
    }

    public boolean finishedAt(long nowMs) {
        return elapsedAt(nowMs) >= totalMs();
    }

    /** Which interval is showing: the one the elapsed time falls in, holding on the last. */
    public int intervalAt(long nowMs) {
        long elapsed = elapsedAt(nowMs);
        long cursor = 0L;
        for (int i = 0; i < preset.intervals.size(); i++) {
            cursor += preset.intervals.get(i).lengthMs;
            if (elapsed < cursor) {
                return i;
            }
        }
        return Math.max(0, preset.intervals.size() - 1);
    }

    /** The interval itself, or null when the preset holds none. */
    public TimerInterval intervalObjectAt(long nowMs) {
        if (preset.intervals.isEmpty()) {
            return null;
        }
        return preset.intervals.get(intervalAt(nowMs));
    }

    /** When the interval showing at this moment began, measured from the start of the preset. */
    public long intervalStartMs(int index) {
        long cursor = 0L;
        for (int i = 0; i < index && i < preset.intervals.size(); i++) {
            cursor += preset.intervals.get(i).lengthMs;
        }
        return cursor;
    }

    public long elapsedInIntervalAt(long nowMs) {
        if (preset.intervals.isEmpty()) {
            return 0L;
        }
        int index = intervalAt(nowMs);
        long into = elapsedAt(nowMs) - intervalStartMs(index);
        long length = preset.intervals.get(index).lengthMs;
        if (into < 0L) {
            return 0L;
        }
        return into > length ? length : into;
    }

    public long remainingInIntervalAt(long nowMs) {
        if (preset.intervals.isEmpty()) {
            return 0L;
        }
        return preset.intervals.get(intervalAt(nowMs)).lengthMs - elapsedInIntervalAt(nowMs);
    }

    /** How far through the current interval, from 0 to 1 — what the bar's colour follows. */
    public float progressInIntervalAt(long nowMs) {
        if (preset.intervals.isEmpty()) {
            return 1f;
        }
        long length = preset.intervals.get(intervalAt(nowMs)).lengthMs;
        return length <= 0L ? 1f : (float) elapsedInIntervalAt(nowMs) / (float) length;
    }

    /** How far through the whole preset — what the bar's fill follows. */
    public float progressAt(long nowMs) {
        long total = totalMs();
        return total <= 0L ? 1f : (float) elapsedAt(nowMs) / (float) total;
    }
}
