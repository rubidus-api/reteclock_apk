package com.reteclock.core;

/**
 * A length of time as the timer shows it, to the second.
 *
 * Hundredths were shown at first, and they were a mistake twice over: they are unreadable on a
 * strip a finger wide, and a readout whose width changes ten times a second cannot be laid out
 * before it is drawn. Seconds change once a second and the widest they will ever be is known in
 * advance, which is what lets the bar decide its lettering once and keep it.
 *
 * Seconds are cut off rather than rounded, because a readout counting down must never show a second
 * it has not reached yet — 0:00:01 with four hundred milliseconds still to run reads as arrival
 * when it is not.
 *
 * Built without any formatter, both because this is called several times a second while the timer
 * runs and because `String.format` on Dalvik allocates a great deal more than a StringBuilder does.
 */
public final class TimeReadout {

    private TimeReadout() {
    }

    /** The full form, `H:MM:SS`, every unit written whether or not it is there. */
    public static String of(long ms) {
        long at = ms < 0L ? 0L : ms;
        StringBuilder out = new StringBuilder(8);
        out.append(at / 3_600_000L).append(':');
        two(out, at / 60_000L % 60L).append(':');
        two(out, at / 1000L % 60L);
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
     * An hour appears only when there is one, and the minutes only when there are minutes or an hour
     * to lead them. So a half-hour preset is `30:00`, the moment it starts is `0`, five seconds in
     * is `5`, and an hour and five minutes is `1:05:00`. Whatever is shown keeps its leading zeros
     * beneath the largest unit shown, so the digits do not jump about: `1:04`, never `1:4`.
     *
     * The bar carries three of these side by side and sizes them together, so trimming them is not
     * tidiness — it is what makes them large enough to read.
     */
    public static String trimmed(long ms) {
        long at = ms < 0L ? 0L : ms;
        long hours = at / 3_600_000L;
        long minutes = at / 60_000L % 60L;
        long seconds = at / 1000L % 60L;

        StringBuilder out = new StringBuilder(8);
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
        return out.toString();
    }
}
