package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings as a piece of text, so they can be carried to another phone or kept as a backup.
 *
 * A phone is replaced, or reset, and everything the user arranged — the presets, the colours, the
 * sizes — is gone. What that arrangement amounts to is a few dozen values, so this writes them
 * down as lines and reads them back.
 *
 * <pre>
 *   reteclock-settings 1
 *   key TAB type TAB value
 * </pre>
 *
 * The type letter is what a preferences file needs to store the value again: {@code b} boolean,
 * {@code i} int, {@code l} long, {@code s} string. Tabs and newlines inside a value are escaped, so
 * a preset — itself a tab-separated format — survives being carried inside this one.
 *
 * <p>Not everything is carried. A running timer, what the speech engine turned out to be on this
 * particular device, and the flags that record what happened last time the clock started all belong
 * to one phone and mean nothing on another; {@link #isPortable} is where that judgement lives.
 */
public final class SettingsText {

    /** The first line, so a file that is not this can be refused rather than half-read. */
    public static final String HEADER = "reteclock-settings 1";

    private static final char FIELD = '\t';

    /** Keys that belong to one device and are never carried to another. */
    private static final String[] LOCAL_ONLY = {
        "timer_run_origin", "timer_run_paused_at", "timer_run_preset",
        "voice_init", "voice_lang",
        "run_unfinished", "safe_notice", "hint_seen", "pool_migrated",
    };

    /** One setting: its name, what kind of value it is, and the value written out. */
    public static final class Entry {
        public final String key;
        /** {@code b}, {@code i}, {@code l} or {@code s}. */
        public final char type;
        public final String value;

        public Entry(String key, char type, String value) {
            this.key = key == null ? "" : key;
            this.type = type;
            this.value = value == null ? "" : value;
        }
    }

    private SettingsText() {
    }

    /**
     * Whether a setting is worth carrying to another phone.
     *
     * Everything is, except the handful that describe this device's own state. Written as a refusal
     * list rather than an allow list on purpose: a setting added later should travel by default,
     * and forgetting to add it to a list is a bug nobody notices until they have restored a backup
     * and found one thing missing.
     */
    public static boolean isPortable(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (String local : LOCAL_ONLY) {
            if (local.equals(key)) {
                return false;
            }
        }
        return true;
    }

    /** The settings as text, in the order given. */
    public static String write(List<Entry> entries) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        if (entries == null) {
            return out.toString();
        }
        for (Entry entry : entries) {
            if (!isPortable(entry.key)) {
                continue;
            }
            out.append(escape(entry.key)).append(FIELD);
            out.append(entry.type).append(FIELD);
            out.append(escape(entry.value)).append('\n');
        }
        return out.toString();
    }

    /** Whether this text is one of ours, before any of it is believed. */
    public static boolean looksLikeSettings(String text) {
        return text != null && text.trim().startsWith(HEADER);
    }

    /**
     * The settings a text holds; empty for anything that is not one of ours.
     *
     * A line that makes no sense is skipped rather than throwing: a file that has lost one line to
     * a bad copy should still restore the other forty.
     */
    public static List<Entry> read(String text) {
        List<Entry> out = new ArrayList<Entry>();
        if (!looksLikeSettings(text)) {
            return out;
        }
        String[] lines = text.split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> fields = split(line);
            if (fields.size() < 3 || fields.get(1).length() != 1) {
                continue;
            }
            char type = fields.get(1).charAt(0);
            if (type != 'b' && type != 'i' && type != 'l' && type != 's') {
                continue;
            }
            String key = unescape(fields.get(0));
            if (!isPortable(key)) {
                continue;
            }
            out.add(new Entry(key, type, unescape(fields.get(2))));
        }
        return out;
    }

    /** Splits a line on unescaped tabs. */
    private static List<String> split(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append('\\').append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == FIELD) {
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
            if (c == '\\' || c == FIELD) {
                out.append('\\').append(c);
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

    /**
     * The names in a stored list that this phone actually has, in their stored order.
     *
     * An arrangement carried from another phone names images and fonts that may not have come with
     * it. Rather than refuse the whole import or leave the clock pointing at files that are not
     * there, the names that are missing are dropped and the user is told how many.
     */
    public static List<String> keepPresent(List<String> stored, List<String> present) {
        List<String> out = new ArrayList<String>();
        if (stored == null || present == null) {
            return out;
        }
        for (String name : stored) {
            if (present.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }
}
