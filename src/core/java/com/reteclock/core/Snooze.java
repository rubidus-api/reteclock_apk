package com.reteclock.core;

/**
 * A bell that has been put off: which bell, and the local stamp it is due to ring again at.
 *
 * <p>Immutable, and arithmetic only — the same stamps {@link Bells} counts in, so the caller asks
 * "did this fall in the window I am looking at" exactly as it asks the bells, and a late tick can
 * neither lose the ring nor deliver it twice.
 *
 * <p><b>How long it lasts, and why that is the design.</b> A put-off ring is a promise the clock can
 * only keep while it is on screen, because the clock's own tick is the only thing that delivers it:
 * there is no {@code AlarmManager} behind this, no service and no wake lock. Leave the clock and the
 * promise goes with it. That is not a gap to be filled in later by accident — it is the line between
 * a chime a person is sitting in front of and an alarm that has to be right at four in the morning
 * on a phone nobody has seen. Filling it is a separate piece of work, written down as one.
 */
public final class Snooze {

    /** The bell that was put off; it rings again exactly as it rang the first time. */
    public final Bell bell;
    /** When it is due, as a {@link Bells#stamp}. */
    public final long dueStamp;

    private Snooze(Bell bell, long dueStamp) {
        this.bell = bell;
        this.dueStamp = dueStamp;
    }

    /**
     * The promise a bell makes when it is put off at this moment, or null when it makes none.
     *
     * A bell with no minutes set offers nothing to put off, so there is nothing to promise. Asking
     * again from an existing promise is how putting it off a second time works: the one ring moves
     * rather than a second one being added.
     */
    public static Snooze of(Bell bell, long nowStamp) {
        if (bell == null || !bell.canSnooze()) {
            return null;
        }
        return new Snooze(bell, nowStamp + bell.snoozeMinutes);
    }

    /**
     * Whether this ring falls in the window {@code (fromStamp, toStamp]}.
     *
     * Half-open at the start and closed at the end, so consecutive windows tile the timeline and the
     * ring belongs to exactly one of them. A window reaching further back than
     * {@link Bells#CATCH_UP_MINUTES} is shortened first, for the reason a bell's is: nobody was
     * there, and a chime for a moment that has gone is worse than silence.
     */
    public boolean isDue(long fromStamp, long toStamp) {
        if (toStamp <= fromStamp) {
            return false;
        }
        long from = Math.max(fromStamp, toStamp - Bells.CATCH_UP_MINUTES);
        return dueStamp > from && dueStamp <= toStamp;
    }
}
