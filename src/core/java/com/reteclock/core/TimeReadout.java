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

    /**
     * The same time with nothing written that is not needed.
     *
     * An hour appears only when there is one; the minutes only when there are minutes or an hour to
     * lead them; the hundredths only when they are not zero. So a half-hour preset is `30:00`, the
     * moment it starts is `0`, four and a bit seconds in is `4.92`, and an hour and five minutes is
     * `1:05:00`. Whatever is shown keeps its leading zeros beneath the largest unit shown, so the
     * digits do not jump about as the numbers change: `1:04.92`, never `1:4.92`.
     *
     * The bar carries three of these side by side, and their size is set by how many characters
     * they come to — so trimming them is not tidiness, it is what makes them large enough to read.
     */
    public static String trimmed(long ms) {
        long at = ms < 0L ? 0L : ms;
        long hours = at / 3_600_000L;
        long minutes = at / 60_000L % 60L;
        long seconds = at / 1000L % 60L;
        long hundredths = at % 1000L / 10L;

        StringBuilder out = new StringBuilder(11);
        if (hours > 0L) {
            out.append(hours).append(':');
            two(out, minutes).append(':');
            two(out, seconds);
        } else if (minutes > 0L) {
            out.append(minutes).append(':');
            two(out, seconds);
        } else {
            out.append(seconds);
        }
        if (hundredths > 0L) {
            out.append('.');
            two(out, hundredths);
        }
        return out.toString();
    }
}
