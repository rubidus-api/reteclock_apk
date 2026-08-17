package com.reteclock.core;

/**
 * One stretch of a timer preset: how long it runs, what it is called, what colour it wears, and
 * what it says when it begins.
 *
 * Immutable, and it makes its own arguments safe. The settings screen validates what the user
 * types, but a value can also arrive from a stored file written by an older version or edited by
 * hand, and a timer that runs for minus five minutes is worse than one that quietly runs for one
 * second.
 */
public final class TimerInterval {

    /** Nothing is shorter than this: a zero-length interval would end in the same instant. */
    public static final long MIN_LENGTH_MS = 1000L;

    /** How long a pre-alarm may be asked for, in seconds; beyond this it is not a warning. */
    public static final int MAX_PRE_ALARM_SECONDS = 3600;

    /** What an interval wears when nobody has chosen: the app's own accent, going to a warning. */
    public static final int DEFAULT_COLOR = 0xFF4DB6AC;
    public static final int DEFAULT_END_COLOR = 0xFFEF5350;

    public final String name;
    public final long lengthMs;
    /** The colour the bar starts this interval in. */
    public final int color;
    /** The colour it has reached by the time the interval ends. */
    public final int endColor;
    /** Spoken when the interval begins; empty says nothing. */
    public final String message;
    /** How many seconds before the end the warning beeps sound; zero means none. */
    public final int preAlarmSeconds;
    /**
     * A stored sound played where the message is spoken — the interval's own beginning.
     *
     * Empty means the built-in beep, and a name whose file is gone means the same: a cue that fell
     * silent because a file was deleted would be a timer that quietly stopped working. The sound and
     * the message are two different things in the same slot, and both happen when both are set.
     */
    public final String startSound;
    /** A stored sound played in place of the warning beeps; empty means the beeps. */
    public final String preAlarmSound;

    public TimerInterval(String name, long lengthMs, int color, int endColor, String message,
            int preAlarmSeconds) {
        this(name, lengthMs, color, endColor, message, preAlarmSeconds, "", "");
    }

    public TimerInterval(String name, long lengthMs, int color, int endColor, String message,
            int preAlarmSeconds, String startSound, String preAlarmSound) {
        this.startSound = startSound == null ? "" : startSound;
        this.preAlarmSound = preAlarmSound == null ? "" : preAlarmSound;
        this.name = name == null ? "" : name;
        this.lengthMs = Math.max(lengthMs, MIN_LENGTH_MS);
        this.color = color == 0 ? DEFAULT_COLOR : color;
        this.endColor = endColor == 0 ? DEFAULT_END_COLOR : endColor;
        this.message = message == null ? "" : message;
        // A pre-alarm longer than the interval itself would sound before it began.
        int wanted = Math.max(preAlarmSeconds, 0);
        int fits = (int) Math.min(wanted, this.lengthMs / 1000L);
        this.preAlarmSeconds = Math.min(fits, MAX_PRE_ALARM_SECONDS);
    }

    /** The same interval with one field replaced; the editor works by making new ones. */
    public TimerInterval withName(String newName) {
        return new TimerInterval(newName, lengthMs, color, endColor, message, preAlarmSeconds,
                startSound, preAlarmSound);
    }

    public TimerInterval withLength(long newLengthMs) {
        return new TimerInterval(name, newLengthMs, color, endColor, message, preAlarmSeconds,
                startSound, preAlarmSound);
    }

    public TimerInterval withColors(int newColor, int newEndColor) {
        return new TimerInterval(name, lengthMs, newColor, newEndColor, message, preAlarmSeconds,
                startSound, preAlarmSound);
    }

    public TimerInterval withMessage(String newMessage) {
        return new TimerInterval(name, lengthMs, color, endColor, newMessage, preAlarmSeconds,
                startSound, preAlarmSound);
    }

    public TimerInterval withPreAlarm(int seconds) {
        return new TimerInterval(name, lengthMs, color, endColor, message, seconds,
                startSound, preAlarmSound);
    }

    public TimerInterval withStartSound(String sound) {
        return new TimerInterval(name, lengthMs, color, endColor, message, preAlarmSeconds,
                sound, preAlarmSound);
    }

    public TimerInterval withPreAlarmSound(String sound) {
        return new TimerInterval(name, lengthMs, color, endColor, message, preAlarmSeconds,
                startSound, sound);
    }

    /** The same interval with every sound it names remapped, the way an import remaps one. */
    public TimerInterval soundsRenamed(java.util.Map<String, String> renames) {
        if (renames == null || renames.isEmpty()) {
            return this;
        }
        String start = renames.containsKey(startSound) ? renames.get(startSound) : startSound;
        String warn = renames.containsKey(preAlarmSound)
                ? renames.get(preAlarmSound) : preAlarmSound;
        return new TimerInterval(name, lengthMs, color, endColor, message, preAlarmSeconds,
                start, warn);
    }
}
