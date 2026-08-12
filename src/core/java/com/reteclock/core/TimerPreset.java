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
 *   preset  := name '|' repeat TAB interval (TAB interval)*
 *   interval:= name | length | colour | endColour | message | preAlarm     (fields joined by '|')
 * </pre>
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

    public TimerPreset(String name, List<TimerInterval> intervals) {
        this(name, intervals, false);
    }

    public TimerPreset(String name, List<TimerInterval> intervals, boolean loops) {
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
        return new TimerPreset(newName, intervals, loops);
    }

    /** The same preset with this list of intervals instead. */
    public TimerPreset withIntervals(List<TimerInterval> newIntervals) {
        return new TimerPreset(name, newIntervals, loops);
    }

    /** The same preset, told whether to start again when it ends. */
    public TimerPreset withLoop(boolean newLoops) {
        return new TimerPreset(name, intervals, newLoops);
    }

    public String toText() {
        // The name's field carries the repeat flag after it, so a preset written before repeating
        // existed still reads — its name simply has no flag, which means it does not repeat.
        StringBuilder out = new StringBuilder(escape(name));
        out.append(FIELD).append(loops ? '1' : '0');
        for (TimerInterval interval : intervals) {
            out.append(PART);
            out.append(escape(interval.name)).append(FIELD);
            out.append(interval.lengthMs).append(FIELD);
            out.append(interval.color).append(FIELD);
            out.append(interval.endColor).append(FIELD);
            out.append(escape(interval.message)).append(FIELD);
            out.append(interval.preAlarmSeconds);
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
                    (int) number(fields.get(5), 0)));
        }
        if (intervals.isEmpty() && name.isEmpty()) {
            return null;
        }
        return new TimerPreset(name, intervals, loops);
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
