package com.reteclock.core;

/**
 * A bell that wakes the phone, put off: which bell, and the instant it rings again (RFC-0012).
 *
 * <p>Unlike {@link Snooze}, which lives as long as the screen does, this promise is written down: the
 * system delivers it, and the process that made it may be long gone when it comes due. What is
 * written is the bell's own stored line, so the promise can check that it is still about a bell
 * that exists — a bell edited or deleted in the meantime drops it, rather than ringing something
 * nobody set.
 */
public final class WakePutOff {

    public final Bell bell;
    public final long dueEpochMillis;

    private WakePutOff(Bell bell, long dueEpochMillis) {
        this.bell = bell;
        this.dueEpochMillis = dueEpochMillis;
    }

    /** The promise a bell makes when put off now, or null when it offers none. */
    public static WakePutOff of(Bell bell, long nowEpochMillis) {
        if (bell == null || !bell.canSnooze()) {
            return null;
        }
        return new WakePutOff(bell, nowEpochMillis + bell.snoozeMinutes * 60_000L);
    }

    /** The instant, a newline, and the bell's stored line. */
    public String text() {
        return dueEpochMillis + "\n" + Bells.lineOf(bell);
    }

    public static WakePutOff parse(String text) {
        if (text == null) {
            return null;
        }
        int newline = text.indexOf('\n');
        if (newline <= 0) {
            return null;
        }
        try {
            long due = Long.parseLong(text.substring(0, newline).trim());
            Bells one = Bells.parse(text.substring(newline + 1));
            return one.size() == 1 ? new WakePutOff(one.list().get(0), due) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The bell in this list the promise was made about, unchanged; null if it is not there. */
    public Bell stillIn(Bells bells) {
        if (bells == null) {
            return null;
        }
        String line = Bells.lineOf(bell);
        for (Bell each : bells.list()) {
            if (Bells.lineOf(each).equals(line)) {
                return each;
            }
        }
        return null;
    }
}
