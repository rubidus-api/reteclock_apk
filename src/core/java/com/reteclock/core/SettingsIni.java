package com.reteclock.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings as an INI file: readable by a person, and parseable by anything.
 *
 * The old format was tab-separated with a type letter in the middle of every line, which a machine
 * reads happily and a person does not. This writes the same values as
 *
 * <pre>
 *   # reteclock settings
 *   [clock]
 *   show_seconds = true
 *   time_percent_wide = 62
 *
 *   [fonts]
 *   font_hour = Inter-Bold.ttf
 * </pre>
 *
 * The sections are the settings pages, because that is the choice the import screen offers: bring
 * the fonts but not the pictures, the timer but not the clock.
 *
 * <p><b>Reading is deliberately forgiving.</b> A file that has been edited by hand — and that is
 * the point of a readable format — arrives with the case changed, the spaces gone, a stray comment,
 * a `:` where the `=` was, Windows line endings, or a section header the writer invented. None of
 * that is an error: keys are matched case-insensitively and trimmed, `#` and `;` start comments,
 * either separator is accepted, and the section a key is found under is ignored in favour of where
 * the key actually belongs. What cannot be guessed is reported rather than assumed — an unknown key
 * or a value of the wrong shape becomes a complaint the import screen can show.
 *
 * <p><b>Types.</b> INI has none, and a preferences file needs them: storing a string where an int
 * lived crashes the next read. So the kind of every key is written down here rather than inferred
 * from the value, and a key this build does not know is skipped instead of guessed at.
 */
public final class SettingsIni {

    /** The first line of a file this wrote. Not required when reading. */
    public static final String HEADER = "# reteclock settings 2";

    /** The pages the settings divide into, which are the sections of the file. */
    public static final String[] SECTIONS = {
        "clock", "fonts", "pictures", "sounds", "timer", "timedate",
    };

    public static final char BOOLEAN = 'b';
    public static final char INT = 'i';
    public static final char LONG = 'l';
    public static final char STRING = 's';

    /** Keys that belong to one device and are never carried to another. */
    private static final String[] LOCAL_ONLY = {
        "timer_run_origin", "timer_run_paused_at", "timer_run_preset",
        "voice_init", "voice_lang",
        "run_unfinished", "safe_notice", "hint_seen", "pool_migrated",
    };

    /** Exact keys, with their kind and their page. */
    private static final String[][] KNOWN = {
        {"show_seconds", "b", "clock"},
        {"date_style", "i", "clock"},
        {"quote_on", "b", "clock"},
        {"clock_only", "b", "clock"},
        {"clock_blink_colon", "b", "clock"},
        {"clock_only_marker", "b", "clock"},
        {"colors_from_theme", "b", "clock"},
        {"burn_in_shift", "b", "clock"},
        {"time_percent_wide", "i", "clock"},
        {"time_percent_tall", "i", "clock"},
        {"text_color", "i", "clock"},
        {"background_color", "i", "clock"},
        {"clock_noon_style", "i", "clock"},
        {"clock_midnight_style", "i", "clock"},
        {"markers", "s", "clock"},
        {"start_when_charging", "b", "clock"},
        {"stay_unlocked", "b", "clock"},
        {"direct_start", "b", "clock"},

        {"font", "s", "fonts"},

        {"background_fit", "i", "pictures"},
        {"background_still_seconds", "i", "pictures"},
        {"background_fade", "b", "pictures"},
        {"background_order_mode", "i", "pictures"},
        {"background_order", "s", "pictures"},
        {"foreground", "s", "pictures"},
        {"pool_background", "s", "pictures"},
        {"pool_text", "s", "pictures"},

        {"sound_clips", "s", "sounds"},
        {"bells", "s", "sounds"},
        {"bells_on", "b", "sounds"},

        {"timer_on", "b", "timer"},
        {"timer_presets", "s", "timer"},
        {"timer_chosen", "i", "timer"},
        {"timer_alert", "i", "timer"},
        {"timer_hidden", "b", "timer"},

        {"calendar_on", "b", "timedate"},
        {"calendar_week_monday", "b", "timedate"},
        {"calendar_header", "i", "timedate"},
        {"calendar_system", "i", "timedate"},
        {"calendar_week_start", "i", "timedate"},
        {"calendar_gregorian_badge", "b", "timedate"},
        {"calendar_hijri_offset", "i", "timedate"},
        {"time_source", "i", "timedate"},
        {"time_utc_offset", "i", "timedate"},
        {"time_dst_preset", "i", "timedate"},
        {"time_dst_custom", "s", "timedate"},
    };

    /** Families of keys, one per field or per calendar: prefix, kind, page. */
    private static final String[][] FAMILIES = {
        {"font_", "s", "fonts"},
        {"text_bold_", "b", "fonts"},
        {"text_italic_", "b", "fonts"},
        {"text_underline_", "b", "fonts"},
        {"text_outline_", "b", "fonts"},
        {"calendar_system_names_", "i", "timedate"},
        {"calendar_system_weekdays_", "i", "timedate"},
        {"names_months_", "s", "timedate"},
        {"names_weekdays_", "s", "timedate"},
    };

    /** One setting on its way in or out. */
    public static final class Entry {
        public final String key;
        /** {@link #BOOLEAN}, {@link #INT}, {@link #LONG} or {@link #STRING}. */
        public final char kind;
        public final String value;
        /** Which page it belongs to, and so which checkbox governs it. */
        public final String section;
        /**
         * The note lines that stood above this setting in the file, kept as they were written.
         *
         * A comment is the one thing in the file that carries no meaning to the program and all of
         * its meaning to the person: "the ones I actually use", "do not turn this on". Rebuilding
         * the file without them would hand somebody back their own arrangement with their own
         * remarks deleted.
         */
        public final List<String> notes;

        public Entry(String key, char kind, String value, String section) {
            this(key, kind, value, section, null);
        }

        public Entry(String key, char kind, String value, String section, List<String> notes) {
            this.key = key;
            this.kind = kind;
            this.value = value == null ? "" : value;
            this.section = section;
            this.notes = notes == null ? new ArrayList<String>() : notes;
        }
    }

    /** What a file turned out to hold, and what could not be made sense of. */
    public static final class Reading {
        public final List<Entry> entries;
        /** One sentence per line that was skipped; empty when the file was wholly understood. */
        public final List<String> complaints;
        /** Notes at the foot of the file, with no setting under them to belong to. */
        public final List<String> trailingNotes;

        Reading(List<Entry> entries, List<String> complaints) {
            this(entries, complaints, new ArrayList<String>());
        }

        Reading(List<Entry> entries, List<String> complaints, List<String> trailingNotes) {
            this.entries = entries;
            this.complaints = complaints;
            this.trailingNotes = trailingNotes;
        }

        /** How many settings this file holds for one page. */
        public int countIn(String section) {
            int count = 0;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).section.equals(section)) {
                    count++;
                }
            }
            return count;
        }
    }

    private SettingsIni() {
    }

    /** Whether a setting is worth carrying to another phone. */
    public static boolean isPortable(String key) {
        return !isLocalOnly(key) && sectionOf(key) != null;
    }

    /** Whether a key describes this device rather than the arrangement on it. */
    public static boolean isLocalOnly(String key) {
        for (int i = 0; i < LOCAL_ONLY.length; i++) {
            if (LOCAL_ONLY[i].equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** The page a key belongs to, or null if this build does not know the key. */
    public static String sectionOf(String key) {
        String[] found = lookup(key);
        return found == null ? null : found[2];
    }

    /** The kind of value a key holds, or 0 if this build does not know the key. */
    public static char kindOf(String key) {
        String[] found = lookup(key);
        return found == null ? 0 : found[1].charAt(0);
    }

    private static String[] lookup(String key) {
        if (key == null) {
            return null;
        }
        for (int i = 0; i < KNOWN.length; i++) {
            if (KNOWN[i][0].equals(key)) {
                return KNOWN[i];
            }
        }
        // Longest prefix wins: "calendar_system_names_3" is a family, "calendar_system" is not.
        String[] best = null;
        for (int i = 0; i < FAMILIES.length; i++) {
            if (key.startsWith(FAMILIES[i][0]) && key.length() > FAMILIES[i][0].length()
                    && (best == null || FAMILIES[i][0].length() > best[0].length())) {
                best = FAMILIES[i];
            }
        }
        return best;
    }

    /**
     * The tab-separated file older versions wrote, read as if it had been INI.
     *
     * Somebody's backup from last month is not their fault, and it holds exactly the same values —
     * only the punctuation changed. The old reader is still here to do the splitting; everything
     * after that is this format's own rules, so a key the old file carried and this build no longer
     * knows is reported the same way it would be in an INI file.
     */
    public static Reading fromOldFormat(String text) {
        StringBuilder ini = new StringBuilder(HEADER).append('\n');
        List<SettingsText.Entry> old = SettingsText.read(text);
        for (int i = 0; i < old.size(); i++) {
            ini.append(old.get(i).key).append(" = ").append(escape(old.get(i).value)).append('\n');
        }
        return parse(ini.toString());
    }

    /** Whether this text is one of those older files. */
    public static boolean isOldFormat(String text) {
        return text != null && text.startsWith(SettingsText.HEADER);
    }

    /** The file, with the sections in a fixed order and the keys sorted inside each. */
    public static String write(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        out.append(HEADER).append('\n');
        out.append("# One key to a line. Lines beginning # or ; are notes.\n");
        out.append("# In a value, \\t is a tab, \\n a line break and \\\\ a backslash.\n");
        for (int s = 0; s < SECTIONS.length; s++) {
            List<Entry> mine = new ArrayList<Entry>();
            for (int i = 0; i < entries.size(); i++) {
                if (SECTIONS[s].equals(entries.get(i).section)) {
                    mine.add(entries.get(i));
                }
            }
            if (mine.isEmpty()) {
                continue;
            }
            sort(mine);
            out.append('\n').append('[').append(SECTIONS[s]).append(']').append('\n');
            for (int i = 0; i < mine.size(); i++) {
                out.append(mine.get(i).key).append(" = ").append(escape(mine.get(i).value))
                        .append('\n');
            }
        }
        return out.toString();
    }

    /**
     * The file as it was understood, written out again.
     *
     * This is what the import screen shows instead of the bytes it was handed. Anything it could
     * not read is not here — which is the point: a preview of the raw text shows what somebody
     * *wrote*, and the question in front of them is what this app is about to *do*. Values appear
     * in the form they will be stored in (`YES` having become `true`), keys are filed under the
     * page they really belong to, and the notes are kept where they stood.
     */
    public static String rebuild(Reading reading) {
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < SECTIONS.length; s++) {
            List<Entry> mine = new ArrayList<Entry>();
            for (int i = 0; i < reading.entries.size(); i++) {
                if (SECTIONS[s].equals(reading.entries.get(i).section)) {
                    mine.add(reading.entries.get(i));
                }
            }
            if (mine.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append('[').append(SECTIONS[s]).append(']').append('\n');
            for (int i = 0; i < mine.size(); i++) {
                Entry entry = mine.get(i);
                for (int n = 0; n < entry.notes.size(); n++) {
                    out.append(entry.notes.get(n)).append('\n');
                }
                out.append(entry.key).append(" = ").append(escape(entry.value)).append('\n');
            }
        }
        if (!reading.trailingNotes.isEmpty()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            for (int i = 0; i < reading.trailingNotes.size(); i++) {
                out.append(reading.trailingNotes.get(i)).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Reads a file back, forgiving everything that can be forgiven.
     *
     * The section headers are read for nothing but company: a key is filed where this build says it
     * belongs, so moving a line between sections by hand cannot put a font setting in the timer.
     */
    public static Reading parse(String text) {
        List<Entry> entries = new ArrayList<Entry>();
        List<String> complaints = new ArrayList<String>();
        if (text == null) {
            return new Reading(entries, complaints);
        }
        List<String> notes = new ArrayList<String>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\r", "").trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.charAt(0) == '#' || line.charAt(0) == ';') {
                // The three lines this format writes about itself are not the user's notes, and
                // repeating them inside the rebuilt file would double them every round trip.
                if (!isOurOwnNote(line)) {
                    notes.add(line);
                }
                continue;
            }
            if (line.charAt(0) == '[') {
                continue;                                  // a header; the key decides, not this
            }
            int cut = separator(line);
            if (cut < 0) {
                complaints.add("line " + (i + 1) + ": no = in \"" + shorten(line) + "\"");
                continue;
            }
            String key = line.substring(0, cut).trim().toLowerCase(java.util.Locale.US);
            String value = unescape(line.substring(cut + 1).trim());
            if (isLocalOnly(key)) {
                continue;                                  // belongs to the other phone, silently
            }
            char kind = kindOf(key);
            if (kind == 0) {
                complaints.add("line " + (i + 1) + ": this version has no setting called \""
                        + shorten(key) + "\"");
                continue;
            }
            String fixed = clean(kind, value);
            if (fixed == null) {
                complaints.add("line " + (i + 1) + ": " + key + " cannot be \"" + shorten(value)
                        + "\"");
                continue;
            }
            entries.add(new Entry(key, kind, fixed, sectionOf(key), notes));
            notes = new ArrayList<String>();
        }
        return new Reading(entries, complaints, notes);
    }

    /** Whether a note is one of the three this format writes at the top of every file. */
    private static boolean isOurOwnNote(String line) {
        return line.equals(HEADER) || line.startsWith("# One key to a line")
                || line.startsWith("# In a value");
    }

    /** The value as it must be stored, or null if it cannot be read as this kind. */
    private static String clean(char kind, String value) {
        if (kind == BOOLEAN) {
            String lower = value.toLowerCase(java.util.Locale.US);
            if ("true".equals(lower) || "yes".equals(lower) || "on".equals(lower)
                    || "1".equals(lower)) {
                return "true";
            }
            if ("false".equals(lower) || "no".equals(lower) || "off".equals(lower)
                    || "0".equals(lower)) {
                return "false";
            }
            return null;
        }
        if (kind == INT || kind == LONG) {
            String digits = value.trim();
            if (digits.length() == 0) {
                return null;
            }
            try {
                if (kind == INT) {
                    Integer.parseInt(digits);
                } else {
                    Long.parseLong(digits);
                }
            } catch (NumberFormatException e) {
                return null;
            }
            return digits;
        }
        return value;
    }

    private static int separator(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }

    private static String shorten(String text) {
        return text.length() <= 40 ? text : text.substring(0, 37) + "...";
    }

    /** A value is one line, so the characters that would end it are written as escapes. */
    public static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '\t') {
                out.append("\\t");
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

    /** The other way, leaving an escape this does not know exactly as it was typed. */
    public static String unescape(String value) {
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                out.append(c);
                continue;
            }
            char next = value.charAt(++i);
            if (next == 't') {
                out.append('\t');
            } else if (next == 'n') {
                out.append('\n');
            } else if (next == 'r') {
                out.append('\r');
            } else if (next == '\\') {
                out.append('\\');
            } else {
                out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    /** Insertion sort by key: the lists are dozens of entries, and this keeps the file diffable. */
    private static void sort(List<Entry> list) {
        for (int i = 1; i < list.size(); i++) {
            Entry entry = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).key.compareTo(entry.key) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, entry);
        }
    }
}
