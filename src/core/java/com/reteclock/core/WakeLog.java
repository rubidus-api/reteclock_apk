package com.reteclock.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The record of what each wake ring did (RFC-0012): how the app admits a failure.
 *
 * <p>A silent failure is the one outcome worse than having no alarm at all. The app cannot stop a
 * phone from killing it, but it can refuse to hide that a ring did not happen — and the lateness
 * column is the only evidence this project can ever have about phones it has never seen.
 *
 * <p>One line per event, {@code kind,due,at,label}, newest last, the oldest dropped past
 * {@link #KEEP}.
 */
public final class WakeLog {

    /** It started sounding. */
    public static final String RANG = "rang";
    /** Somebody pressed Stop. */
    public static final String STOPPED = "stopped";
    /** Somebody put it off. */
    public static final String PUT_OFF = "put_off";
    /** Nobody answered; it ended by itself. */
    public static final String UNANSWERED = "unanswered";
    /** Delivered past {@link WakeSchedule#LATE_LIMIT_MS}, and left silent. */
    public static final String LATE = "late";
    /** Armed, and never delivered at all. */
    public static final String MISSED = "missed";
    /** Rang before the phone was first unlocked, so with the built-in sound. */
    public static final String LOCKED = "locked";
    /** A put-off whose bell had changed or gone; not rung. */
    public static final String DROPPED = "dropped";

    private static final List<String> KINDS = Arrays.asList(
            RANG, STOPPED, PUT_OFF, UNANSWERED, LATE, MISSED, LOCKED, DROPPED);

    /** How many lines are kept. */
    public static final int KEEP = 200;

    private WakeLog() {
    }

    public static final class Entry {
        public final String kind;
        public final long dueEpochMillis;
        public final long atEpochMillis;
        public final String label;

        public Entry(String kind, long dueEpochMillis, long atEpochMillis, String label) {
            this.kind = kind;
            this.dueEpochMillis = dueEpochMillis;
            this.atEpochMillis = atEpochMillis;
            this.label = label == null ? "" : label;
        }

        public long latenessMillis() {
            return atEpochMillis - dueEpochMillis;
        }

        public String line() {
            return kind + "," + dueEpochMillis + "," + atEpochMillis + "," + escape(label);
        }

        public static Entry parse(String line) {
            if (line == null) {
                return null;
            }
            String[] parts = line.split(",", 4);
            if (parts.length < 4 || !KINDS.contains(parts[0])) {
                return null;
            }
            try {
                return new Entry(parts[0], Long.parseLong(parts[1].trim()),
                        Long.parseLong(parts[2].trim()), unescape(parts[3]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static List<Entry> parseAll(String text) {
        List<Entry> out = new ArrayList<Entry>();
        if (text == null) {
            return out;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Entry entry = Entry.parse(lines[i]);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    /** The text with only the newest {@link #KEEP} readable lines. */
    public static String trimmed(String text) {
        List<Entry> all = parseAll(text);
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, all.size() - KEEP); i < all.size(); i++) {
            out.append(all.get(i).line()).append('\n');
        }
        return out.toString();
    }

    /**
     * Whether a wake-up armed for {@code armedDue} was never heard from: its time is past the late
     * limit and the record holds nothing about it.
     */
    public static boolean isMissed(long armedDue, long nowEpochMillis, List<Entry> entries) {
        if (armedDue <= 0 || nowEpochMillis - armedDue <= WakeSchedule.LATE_LIMIT_MS) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.dueEpochMillis == armedDue) {
                return false;
            }
        }
        return true;
    }

    private static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == ',') {
                out.append("\\c");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                out.append(next == 'c' ? ',' : next == 'n' ? '\n' : next == 'r' ? '\r' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
