package com.reteclock.core;

/**
 * How a run is handed from one screen to another.
 *
 * The clock and the screensaver are separate windows, and a timer started on one should still be
 * running when the other appears. A run is three numbers — which preset, when it began, and when it
 * was paused — so handing it over needs no state machine, only somewhere to write them and a rule
 * for when they have stopped meaning anything.
 *
 * The times are `elapsedRealtime`, which counts from the device booting. That makes the rule simple
 * and reliable: a stored origin *later* than the current moment can only have come from before a
 * reboot, and belongs to a device that no longer exists.
 */
public final class TimerMemory {

    /** No run stored. */
    public static final long NONE = Long.MIN_VALUE;

    /**
     * A running timer older than this is not picked up again.
     *
     * A pomodoro left running for half a day was not left running on purpose; resuming it would
     * show a bar full to the brim and sound nothing, which tells the user less than starting fresh.
     * A *paused* run is exempt: pausing is deliberate, and it is not going anywhere.
     */
    public static final long FORGET_AFTER_MS = 12L * 60L * 60L * 1000L;

    private TimerMemory() {
    }

    /**
     * What to write down for the moment it began — the run's own origin, which already has any
     * pause folded into it, so a resumed run is handed over exactly where it stands.
     */
    public static long originOf(TimerRun run, long nowMs) {
        return run.originMs();
    }

    /** What to write down for the pause: when it stopped, or -1 while it is running. */
    public static long pausedAtOf(TimerRun run) {
        return run.pausedAtMs();
    }

    /**
     * The run those numbers describe, or null when there is nothing worth restoring.
     *
     * @param preset the preset it was running; null means nothing to restore
     * @param originMs what {@link #originOf} wrote, or {@link #NONE}
     * @param pausedAtMs what {@link #pausedAtOf} wrote
     * @param nowMs the current `elapsedRealtime`
     */
    /**
     * A short name for a preset, stored beside a run so it can be told whose it was.
     *
     * Presets have no identifiers — they are a list the user edits — so this stands in for one: the
     * name and the total length. Change either, or pick a different preset, and the stored run no
     * longer matches and is not taken up. Without this a run stored from one preset was restored
     * against whichever preset happened to be chosen later, and rang at times belonging to neither.
     */
    public static String identityOf(TimerPreset preset) {
        return preset == null ? "" : preset.totalMs() + ":" + preset.name;
    }

    /** The stored run, but only if it belongs to this preset. */
    public static TimerRun restore(TimerPreset preset, String identity, long originMs,
            long pausedAtMs, long nowMs) {
        if (!identityOf(preset).equals(identity == null ? "" : identity)) {
            return null;
        }
        return restore(preset, originMs, pausedAtMs, nowMs);
    }

    public static TimerRun restore(TimerPreset preset, long originMs, long pausedAtMs,
            long nowMs) {
        if (preset == null || originMs == NONE) {
            return null;
        }
        if (originMs > nowMs) {
            // The device has rebooted since; this run is from a previous life.
            return null;
        }
        boolean paused = pausedAtMs >= 0L;
        if (!paused && nowMs - originMs > FORGET_AFTER_MS) {
            return null;
        }
        TimerRun run = TimerRun.start(preset, originMs);
        return paused ? run.pausedAt(pausedAtMs) : run;
    }
}
