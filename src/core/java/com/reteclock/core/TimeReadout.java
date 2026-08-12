package com.reteclock.core;

/**
 * A length of time as the timer shows it: {@code H:MM:SS.hh}.
 *
 * Hundredths are cut off rather than rounded, because a readout counting down must never show the
 * second it has not reached yet — 0:00:01.00 with four milliseconds still to run reads as arrival
 * when it is not.
 *
 * Built without any formatter, both because this is called several times a second while the timer
 * runs and because `String.format` on Dalvik allocates a great deal more than a StringBuilder does.
 */
public final class TimeReadout {

    private TimeReadout() {
    }

    public static String of(long ms) {
        long at = ms < 0L ? 0L : ms;
        long hours = at / 3_600_000L;
        long minutes = at / 60_000L % 60L;
        long seconds = at / 1000L % 60L;
        long hundredths = at % 1000L / 10L;

        StringBuilder out = new StringBuilder(11);
        out.append(hours).append(':');
        two(out, minutes).append(':');
        two(out, seconds).append('.');
        two(out, hundredths);
        return out.toString();
    }

    private static StringBuilder two(StringBuilder out, long value) {
        if (value < 10L) {
            out.append('0');
        }
        return out.append(value);
    }
}
