package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named, ordered run of intervals — one pomodoro, one tea, one workout.
 *
 * It writes itself as text and reads itself back, because the settings are `SharedPreferences` and
 * a preset is a small tree. Adding a JSON library for it would be a dependency; using the
 * framework's own `org.json` would put the model out of reach of the JVM tests, which is where
 * everything in this package is checked. So: a text format, escaped, and tested in both directions
 * against the awkward characters a person can actually type.
 *
 * <pre>
 *   preset  := name '|' repeat '|' startSound '|' finishSound TAB interval (TAB interval)*
 *   interval:= name | length | colour | endColour | message | preAlarm | startSound | preAlarmSound
 * </pre>
 *
 * Fields were added to both lines as the timer grew, and both are read by position with the tail
 * optional: a preset written before sounds existed simply has none, and one written after is read by
 * an older build as the preset it always was.
 *
 * Backslash escapes itself, the two separators, and the newline, so a name containing any of them
 * survives the round trip.
 */
public final class TimerPreset {

    private static final char PART = '\t';
    private static final char FIELD = '|';

    public final String name;
    public final List<TimerInterval> intervals;
    /** Whether it starts again the moment it ends, forever. */
    public final boolean loops;
    /** A stored sound played when the whole preset begins; empty means the built-in beep. */
    public final String startSound;
    /** And when it is done, in place of the finishing melody. */
    public final String finishSound;

    public TimerPreset(String name, List<TimerInterval> intervals) {
        this(name, intervals, false);
    }

    public TimerPreset(String name, List<TimerInterval> intervals, boolean loops) {
        this(name, intervals, loops, "", "");
    }

    public TimerPreset(String name, List<TimerInterval> intervals, boolean loops,
            String startSound, String finishSound) {
        this.startSound = startSound == null ? "" : startSound;
        this.finishSound = finishSound == null ? "" : finishSound;
        this.loops = loops;
        this.name = name == null ? "" : name;
        this.intervals = Collections.unmodifiableList(
                new ArrayList<TimerInterval>(intervals == null
                        ? new ArrayList<TimerInterval>()
                        : intervals));
    }

    /** How long the whole preset runs. */
    public long totalMs() {
        long total = 0L;
        for (TimerInterval interval : intervals) {
            total += interval.lengthMs;
        }
        return total;
    }

    /**
     * Where an interval begins, as a fraction of the whole preset.
     *
     * The bar draws the whole preset at once — every interval in its own colours — so it needs to
     * know where each one sits along the length. Kept here rather than in the view because it is
     * arithmetic on the model, and arithmetic on the model is the part worth testing.
     */
    public float startFraction(int index) {
        long total = totalMs();
        if (total <= 0L || index <= 0) {
            return 0f;
        }
        long before = 0L;
        for (int i = 0; i < index && i < intervals.size(); i++) {
            before += intervals.get(i).lengthMs;
        }
        return (float) ((double) before / total);
    }

    /** Where it ends, as a fraction of the whole preset. */
    public float endFraction(int index) {
        if (index >= intervals.size() - 1) {
            return intervals.isEmpty() || totalMs() <= 0L ? 0f : 1f;
        }
        return startFraction(index + 1);
    }

    /** The same preset under another name. */
    public TimerPreset withName(String newName) {
        return new TimerPreset(newName, intervals, loops, startSound, finishSound);
    }

    /** The same preset with this list of intervals instead. */
    public TimerPreset withIntervals(List<TimerInterval> newIntervals) {
        return new TimerPreset(name, newIntervals, loops, startSound, finishSound);
    }

    /** The same preset, told whether to start again when it ends. */
    public TimerPreset withLoop(boolean newLoops) {
        return new TimerPreset(name, intervals, newLoops, startSound, finishSound);
    }

    public TimerPreset withStartSound(String sound) {
        return new TimerPreset(name, intervals, loops, sound, finishSound);
    }

    public TimerPreset withFinishSound(String sound) {
        return new TimerPreset(name, intervals, loops, startSound, sound);
    }

    /**
     * The same preset with every sound name it holds remapped — its own two and its intervals'.
     *
     * An imported package can land a file under a different name when the one it wanted was taken,
     * and a preset naming the old one would be a preset whose sounds silently became beeps.
     */
    public TimerPreset soundsRenamed(java.util.Map<String, String> renames) {
        if (renames == null || renames.isEmpty()) {
            return this;
        }
        List<TimerInterval> moved = new ArrayList<TimerInterval>(intervals.size());
        for (int i = 0; i < intervals.size(); i++) {
            moved.add(intervals.get(i).soundsRenamed(renames));
        }
        String start = renames.containsKey(startSound) ? renames.get(startSound) : startSound;
        String finish = renames.containsKey(finishSound) ? renames.get(finishSound) : finishSound;
        return new TimerPreset(name, moved, loops, start, finish);
    }

    /** Every sound this preset names, its intervals included, with no empties and no repeats. */
    public List<String> soundNames() {
        List<String> out = new ArrayList<String>();
        addSound(out, startSound);
        addSound(out, finishSound);
        for (int i = 0; i < intervals.size(); i++) {
            addSound(out, intervals.get(i).startSound);
            addSound(out, intervals.get(i).preAlarmSound);
        }
        return out;
    }

    private static void addSound(List<String> out, String name) {
        if (name != null && !name.isEmpty() && !out.contains(name)) {
            out.add(name);
        }
    }

    public String toText() {
        // The name's field carries the repeat flag after it, so a preset written before repeating
        // existed still reads — its name simply has no flag, which means it does not repeat.
        StringBuilder out = new StringBuilder(escape(name));
        out.append(FIELD).append(loops ? '1' : '0');
        out.append(FIELD).append(escape(startSound));
        out.append(FIELD).append(escape(finishSound));
        for (TimerInterval interval : intervals) {
            out.append(PART);
            out.append(escape(interval.name)).append(FIELD);
            out.append(interval.lengthMs).append(FIELD);
            out.append(interval.color).append(FIELD);
            out.append(interval.endColor).append(FIELD);
            out.append(escape(interval.message)).append(FIELD);
            out.append(interval.preAlarmSeconds).append(FIELD);
            out.append(escape(interval.startSound)).append(FIELD);
            out.append(escape(interval.preAlarmSound));
        }
        return out.toString();
    }

    /** Reads one preset, or null for anything that is not one. */
    public static TimerPreset parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        List<String> parts = split(text, PART);
        List<String> head = split(parts.get(0), FIELD);
        String name = unescape(head.get(0));
        boolean loops = head.size() > 1 && "1".equals(head.get(1).trim());
        String startSound = head.size() > 2 ? unescape(head.get(2)) : "";
        String finishSound = head.size() > 3 ? unescape(head.get(3)) : "";
        List<TimerInterval> intervals = new ArrayList<TimerInterval>();
        for (int i = 1; i < parts.size(); i++) {
            List<String> fields = split(parts.get(i), FIELD);
            if (fields.size() < 6) {
                continue;
            }
            intervals.add(new TimerInterval(
                    unescape(fields.get(0)),
                    number(fields.get(1), TimerInterval.MIN_LENGTH_MS),
                    (int) number(fields.get(2), TimerInterval.DEFAULT_COLOR),
                    (int) number(fields.get(3), TimerInterval.DEFAULT_END_COLOR),
                    unescape(fields.get(4)),
                    (int) number(fields.get(5), 0),
                    fields.size() > 6 ? unescape(fields.get(6)) : "",
                    fields.size() > 7 ? unescape(fields.get(7)) : ""));
        }
        if (intervals.isEmpty() && name.isEmpty()) {
            return null;
        }
        return new TimerPreset(name, intervals, loops, startSound, finishSound);
    }

    private static long number(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Splits on a separator that a backslash can protect. */
    static List<String> split(String text, char separator) {
        List<String> out = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append('\\').append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == separator) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escaped) {
            current.append('\\');
        }
        out.add(current.toString());
        return out;
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == PART || c == FIELD || c == '\n' || c == '\r') {
                out.append('\\').append(c == '\n' ? 'n' : c == '\r' ? 'r' : c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            out.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next);
        }
        return out.toString();
    }
}
