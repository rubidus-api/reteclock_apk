package com.reteclock.core;

/**
 * The months and weekdays as the user wrote them, where the user wrote any.
 *
 * The names that ship are deliberately narrow: Latin letters and ordinary digits, three or four
 * characters, so that every calendar fits the same grid on the same old screens. That is the right
 * default and the wrong law — somebody who wants their own language in their own clock should have
 * it, and it is their screen.
 *
 * So this holds names with **no limit on length and no limit on script**, and the settings screen
 * says plainly what that costs: past four characters, or outside ASCII where the chosen font has no
 * glyph, the grid can crowd or the writing can come out as boxes. The clock does not police it. A
 * name left blank falls back to the built-in one, so a user can rename March and leave the rest.
 *
 * Stored as one line per list, names separated by tabs — a tab is the one separator that cannot
 * appear in a name typed into a single-line field and is legal in the XML the preferences live in.
 *
 * Pure Java: no android.*, no java.util.*.
 */
public final class CustomNames {

    /** No names of the user's own: every lookup falls through to the built-in name. */
    public static final CustomNames NONE = new CustomNames(new String[0], new String[0]);

    private static final char SEPARATOR = '\t';

    /** Index 0 is the first month of the calendar's own year. */
    private final String[] months;
    /** Index 0 is Sunday, as everywhere else in this app. */
    private final String[] weekdays;

    private CustomNames(String[] months, String[] weekdays) {
        this.months = months;
        this.weekdays = weekdays;
    }

    /** The two lists as they came from the settings screen; either may be empty. */
    public static CustomNames of(String[] months, String[] weekdays) {
        return new CustomNames(copy(months), copy(weekdays));
    }

    /** Reads back what {@link #monthsText} and {@link #weekdaysText} wrote. */
    public static CustomNames parse(String monthsText, String weekdaysText) {
        return new CustomNames(split(monthsText), split(weekdaysText));
    }

    public String monthsText() {
        return join(months);
    }

    public String weekdaysText() {
        return join(weekdays);
    }

    /** Whether anything at all was written; an all-blank list counts as nothing. */
    public boolean isEmpty() {
        return blank(months) && blank(weekdays);
    }

    /** How many month names are held, which is what the editing screen redraws. */
    public int monthCount() {
        return months.length;
    }

    /** The user's name for a month, 1-based, or the fallback where they wrote none. */
    public String month(int month, String fallback) {
        return pick(months, month - 1, fallback);
    }

    /** The user's name for a weekday, 0 for Sunday, or the fallback. */
    public String weekday(int index, String fallback) {
        return pick(weekdays, index, fallback);
    }

    /** The raw entry, blank where the user wrote none: what a text field is filled with. */
    public String monthEntry(int month) {
        return pick(months, month - 1, "");
    }

    public String weekdayEntry(int index) {
        return pick(weekdays, index, "");
    }

    /** The same names with one month replaced; a blank string means "use the built-in one". */
    public CustomNames withMonth(int month, String name, int howMany) {
        return new CustomNames(replace(months, month - 1, name, howMany), weekdays);
    }

    public CustomNames withWeekday(int index, String name) {
        return new CustomNames(months, replace(weekdays, index, name, 7));
    }

    private static String pick(String[] list, int index, String fallback) {
        if (index < 0 || index >= list.length) {
            return fallback;
        }
        String name = list[index];
        return name == null || name.length() == 0 ? fallback : name;
    }

    private static String[] replace(String[] list, int index, String name, int howMany) {
        int size = Math.max(howMany, Math.max(list.length, index + 1));
        String[] out = new String[size];
        for (int i = 0; i < size; i++) {
            out[i] = i < list.length && list[i] != null ? list[i] : "";
        }
        // A tab would split one name into two on the way back in, so it is the one character that
        // cannot survive being typed. Everything else does, whatever alphabet it is in.
        out[index] = name == null ? "" : name.replace(SEPARATOR, ' ');
        return out;
    }

    private static String[] copy(String[] list) {
        if (list == null) {
            return new String[0];
        }
        String[] out = new String[list.length];
        for (int i = 0; i < list.length; i++) {
            out[i] = list[i] == null ? "" : list[i];
        }
        return out;
    }

    private static boolean blank(String[] list) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] != null && list[i].length() > 0) {
                return false;
            }
        }
        return true;
    }

    private static String join(String[] list) {
        if (blank(list)) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < list.length; i++) {
            if (i > 0) {
                out.append(SEPARATOR);
            }
            out.append(list[i] == null ? "" : list[i]);
        }
        return out.toString();
    }

    private static String[] split(String text) {
        if (text == null || text.length() == 0) {
            return new String[0];
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == SEPARATOR) {
                count++;
            }
        }
        String[] out = new String[count];
        int at = 0;
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == SEPARATOR) {
                out[at++] = text.substring(start, i);
                start = i + 1;
            }
        }
        return out;
    }
}
