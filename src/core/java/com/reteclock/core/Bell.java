package com.reteclock.core;

/**
 * One bell: a sound, a time of day, and the days of the week it rings on.
 *
 * Not an alarm clock, and the line is drawn on purpose. A bell may be put off for a few minutes
 * ({@link #snoozeMinutes}) and stopped, because both of those are answers a person gives to a sound
 * they can hear. What it still does not do is ring while nobody is looking: there is no
 * notification, no wake lock and nothing scheduled outside the clock's own tick, so a bell rings
 * while the clock or the screensaver is on screen and not otherwise. Everything an alarm clock adds
 * beyond that is a thing that has to be right at four in the morning on a phone nobody has seen.
 *
 * <p>Immutable, and it makes its own arguments safe: a bell can arrive from a file another phone
 * wrote or a person edited.
 */
public final class Bell {

    /** Sunday is bit 0, Saturday is bit 6 — the same numbering {@link CivilTime#weekday} uses. */
    public static final int EVERY_DAY = 0x7F;
    public static final int NO_DAY = 0;

    public static final int MINUTES_A_DAY = 24 * 60;

    /** Whether it is switched on. A bell that is off keeps its time and its days. */
    public final boolean on;
    /** The days it rings, as a bit per weekday. */
    public final int days;
    /** When, in minutes from local midnight. */
    public final int minuteOfDay;
    /** The stored sound it plays; empty means the built-in chime. */
    public final String sound;
    /** What the user calls it; empty is allowed and the screen then shows the time. */
    public final String label;
    /**
     * How many times the sound plays when the bell rings.
     *
     * A chime of two or three seconds is easy to miss once and unmistakable three times over, and
     * the alternative — a longer file — is not something a person should have to edit audio to get.
     * One is the ordinary answer and what every bell set before this existed keeps.
     */
    public final int repeats;

    /** Nothing repeats more than this: past it, it is not a bell, it is an alarm. */
    public static final int MAX_REPEATS = 10;

    /**
     * How long a bell that has been put off waits before it rings again, in minutes; 0 is none.
     *
     * A bell with none behaves as every bell did before this existed: it rings, a touch stops it,
     * and that is all. That is also what a bell set on an older version reads back as, so nobody's
     * morning changes underneath them.
     */
    public final int snoozeMinutes;

    /** The longest a bell may be put off. Beyond half an hour it is not a chime being deferred. */
    public static final int MAX_SNOOZE_MINUTES = 30;

    /** What a bell offers when the screen makes a new one. */
    public static final int DEFAULT_SNOOZE_MINUTES = 10;

    public Bell(boolean on, int days, int minuteOfDay, String sound, String label) {
        this(on, days, minuteOfDay, sound, label, 1);
    }

    public Bell(boolean on, int days, int minuteOfDay, String sound, String label, int repeats) {
        this(on, days, minuteOfDay, sound, label, repeats, 0);
    }

    public Bell(boolean on, int days, int minuteOfDay, String sound, String label, int repeats,
            int snoozeMinutes) {
        this.on = on;
        this.days = days & EVERY_DAY;
        int minute = minuteOfDay % MINUTES_A_DAY;
        this.minuteOfDay = minute < 0 ? minute + MINUTES_A_DAY : minute;
        this.sound = sound == null ? "" : sound;
        this.label = label == null ? "" : label;
        this.repeats = repeats < 1 ? 1 : repeats > MAX_REPEATS ? MAX_REPEATS : repeats;
        this.snoozeMinutes = snoozeMinutes < 1 ? 0
                : snoozeMinutes > MAX_SNOOZE_MINUTES ? MAX_SNOOZE_MINUTES : snoozeMinutes;
    }

    /**
     * A new bell as the screen offers it: on, every day, at the hour, with no sound chosen yet.
     *
     * It offers to be put off, because a bell being made now is being made by somebody who has the
     * choice in front of them. A bell made before the choice existed keeps none — the owner's
     * decision, and the reason the stored form defaults to zero rather than to this.
     */
    public static Bell atHour(int hour) {
        return new Bell(true, EVERY_DAY, (hour % 24) * 60, "", "", 1, DEFAULT_SNOOZE_MINUTES);
    }

    /** Whether this bell offers to be put off rather than only stopped. */
    public boolean canSnooze() {
        return snoozeMinutes > 0;
    }

    public int hour() {
        return minuteOfDay / 60;
    }

    public int minute() {
        return minuteOfDay % 60;
    }

    /** Whether it rings on this weekday, 0 for Sunday. */
    public boolean ringsOn(int weekday) {
        if (weekday < 0 || weekday > 6) {
            return false;
        }
        return (days & (1 << weekday)) != 0;
    }

    /** Whether it can ever ring: switched on, and with a day to ring on. */
    public boolean isLive() {
        return on && days != NO_DAY;
    }

    public Bell withOn(boolean nowOn) {
        return new Bell(nowOn, days, minuteOfDay, sound, label, repeats, snoozeMinutes);
    }

    public Bell withDays(int nowDays) {
        return new Bell(on, nowDays, minuteOfDay, sound, label, repeats, snoozeMinutes);
    }

    /** The same bell with one weekday turned on or off. */
    public Bell withDay(int weekday, boolean rings) {
        if (weekday < 0 || weekday > 6) {
            return this;
        }
        int bit = 1 << weekday;
        return withDays(rings ? days | bit : days & ~bit);
    }

    public Bell withTime(int hour, int minute) {
        return new Bell(on, days, hour * 60 + minute, sound, label, repeats, snoozeMinutes);
    }

    public Bell withSound(String name) {
        return new Bell(on, days, minuteOfDay, name, label, repeats, snoozeMinutes);
    }

    /** The same bell, played a different number of times when it rings. */
    public Bell withRepeats(int times) {
        return new Bell(on, days, minuteOfDay, sound, label, times, snoozeMinutes);
    }

    /** The same bell, put off for a different number of minutes; 0 takes the choice away. */
    public Bell withSnooze(int minutes) {
        return new Bell(on, days, minuteOfDay, sound, label, repeats, minutes);
    }

    public Bell withLabel(String text) {
        return new Bell(on, days, minuteOfDay, sound, text, repeats, snoozeMinutes);
    }
}
