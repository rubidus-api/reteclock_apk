package com.reteclock.core;

/**
 * When the clock says the hour (issue #67).
 *
 * <p>Asked once a second by the screen showing the clock, with the moment it last asked: is there a
 * round hour, by the clock's own reckoning, after that moment and up to now? The hour is the
 * clock's — its own offset, so a clock set half an hour away from the phone says its hours at half
 * past the phone's — and it is said only as it turns. A clock that was away while an hour turned
 * comes back saying nothing: an announcement twenty minutes late is not the time.
 */
public final class HourChime {

    /** What a screen that has not looked yet passes as the last moment. */
    public static final long NEVER = Long.MIN_VALUE;

    /** How late a turned hour may still be said: a slow frame, not a clock that was away. */
    public static final long LATE_MS = 60_000L;

    private static final long HOUR_MS = 3_600_000L;

    private HourChime() {
    }

    /**
     * Whether an hour turned after {@code lastMs} and by {@code nowMs}, recently enough to say.
     *
     * @param lastMs        when the screen last asked, or {@link #NEVER}
     * @param nowMs         now, milliseconds since the epoch
     * @param offsetMinutes the clock's own offset from UTC
     */
    public static boolean due(long lastMs, long nowMs, int offsetMinutes) {
        if (lastMs == NEVER || nowMs <= lastMs) {
            return false;
        }
        long offset = offsetMinutes * 60_000L;
        long local = nowMs + offset;
        // Rounded down for a moment before 1970 too; Math.floorDiv is not on Android before 7.
        long hours = local / HOUR_MS;
        if (local % HOUR_MS < 0) {
            hours--;
        }
        long turned = hours * HOUR_MS - offset;
        return turned > lastMs && turned <= nowMs && nowMs - turned < LATE_MS;
    }
}
