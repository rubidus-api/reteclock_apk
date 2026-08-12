package com.reteclock.core;

/**
 * A length as somebody types it: hours, minutes and seconds.
 *
 * Asking for a length in seconds is arithmetic homework — twenty-five minutes is 1500, and getting
 * it wrong by a factor of sixty is easy and silent. Three fields ask for what the user already
 * knows, and this turns them into a length and back again. Milliseconds were a fourth field for one
 * release and nobody wants to set a timer to the millisecond.
 *
 * Deliberately lenient. The fields are free text, so a blank one is nothing, a negative one is
 * nothing, and ninety in the seconds box is a minute and a half rather than an error: nobody should
 * be refused for typing a number that means exactly what it appears to mean.
 */
public final class TimeInput {

    private TimeInput() {
    }

    /** The three fields as one length. */
    public static long msOf(int hours, int minutes, int seconds) {
        long total = 0L;
        total += Math.max(hours, 0) * 3_600_000L;
        total += Math.max(minutes, 0) * 60_000L;
        total += Math.max(seconds, 0) * 1000L;
        return total;
    }

    public static int hoursOf(long ms) {
        return (int) (Math.max(ms, 0L) / 3_600_000L);
    }

    public static int minutesOf(long ms) {
        return (int) (Math.max(ms, 0L) / 60_000L % 60L);
    }

    public static int secondsOf(long ms) {
        return (int) (Math.max(ms, 0L) / 1000L % 60L);
    }


    /** What one field holds, or the fallback when it holds nothing a number can be made of. */
    public static int number(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
