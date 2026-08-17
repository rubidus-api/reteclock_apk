package com.reteclock.core;

/**
 * One bell: a sound, a time of day, and the days of the week it rings on.
 *
 * Not an alarm clock. It rings, and touching the screen stops it — there is nothing to dismiss, no
 * snooze, no notification. That is deliberate: this is the hour chime of a clock somebody has
 * standing on a desk, and everything an alarm clock adds beyond ringing is a thing that has to be
 * right at four in the morning.
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

    public Bell(boolean on, int days, int minuteOfDay, String sound, String label) {
        this.on = on;
        this.days = days & EVERY_DAY;
        int minute = minuteOfDay % MINUTES_A_DAY;
        this.minuteOfDay = minute < 0 ? minute + MINUTES_A_DAY : minute;
        this.sound = sound == null ? "" : sound;
        this.label = label == null ? "" : label;
    }

    /** A new bell as the screen offers it: on, every day, at the hour, with no sound chosen yet. */
    public static Bell atHour(int hour) {
        return new Bell(true, EVERY_DAY, (hour % 24) * 60, "", "");
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
        return new Bell(nowOn, days, minuteOfDay, sound, label);
    }

    public Bell withDays(int nowDays) {
        return new Bell(on, nowDays, minuteOfDay, sound, label);
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
        return new Bell(on, days, hour * 60 + minute, sound, label);
    }

    public Bell withSound(String name) {
        return new Bell(on, days, minuteOfDay, name, label);
    }

    public Bell withLabel(String text) {
        return new Bell(on, days, minuteOfDay, sound, text);
    }
}
