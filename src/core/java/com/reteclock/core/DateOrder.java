package com.reteclock.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The order of the date line under a landscape clock (issue #42, R87).
 *
 * On a wide screen with no calendar the weekday, the date, the year and the seconds used to stand
 * in a column beside the time. They are one line under it now, and which comes first is the user's
 * to say — different countries read a date in different orders, and no default is right everywhere.
 *
 * The month and the day travel together as one item. How they are written together is already
 * settled elsewhere — named or numeric, day first or month first, leading zeros — and a second
 * place deciding it would be a second answer to the same question.
 *
 * The order is written to the settings file, so it can come back short, doubled, misspelt or empty.
 * A clock with no date on it because a file was hand-edited is a fault the user could not undo from
 * the screen, so this repairs what it is given: what the text did say is kept, in the order it said
 * it, and whatever it failed to say is appended in the default order.
 */
public final class DateOrder {

    /** The items, in the order a date is written where this clock came from. */
    private static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            ClockLayout.ROLE_WEEKDAY,
            ClockLayout.ROLE_MONTH_DAY,
            ClockLayout.ROLE_YEAR,
            ClockLayout.ROLE_SECOND));

    /** Weekday, date, year, seconds — the reading order of the lines this line replaced. */
    public static final DateOrder DEFAULT = new DateOrder(ALL);

    private final List<String> fields;

    private DateOrder(List<String> fields) {
        this.fields = Collections.unmodifiableList(new ArrayList<String>(fields));
    }

    /** Every item, in the user's order. Never short, never doubled. */
    public List<String> fields() {
        return fields;
    }

    /** The items actually drawn: the seconds are on the line only while they are switched on. */
    public List<String> shown(boolean showSeconds) {
        List<String> out = new ArrayList<String>(fields.size());
        for (String field : fields) {
            if (showSeconds || !ClockLayout.ROLE_SECOND.equals(field)) {
                out.add(field);
            }
        }
        return out;
    }

    /** An order from a list, repaired the same way a stored one is. */
    public static DateOrder of(List<String> wanted) {
        List<String> out = new ArrayList<String>(ALL.size());
        if (wanted != null) {
            for (String field : wanted) {
                if (ALL.contains(field) && !out.contains(field)) {
                    out.add(field);
                }
            }
        }
        for (String field : ALL) {
            if (!out.contains(field)) {
                out.add(field);
            }
        }
        return new DateOrder(out);
    }

    /** The order as the settings file holds it: the item names, comma separated. */
    public String text() {
        StringBuilder out = new StringBuilder();
        for (String field : fields) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(field);
        }
        return out.toString();
    }

    /** An order read back from {@link #text()}, or the default where the text says nothing. */
    public static DateOrder parse(String text) {
        if (text == null) {
            return DEFAULT;
        }
        List<String> wanted = new ArrayList<String>(ALL.size());
        for (String piece : text.split(",")) {
            String field = piece.trim();
            if (!field.isEmpty()) {
                wanted.add(field);
            }
        }
        return of(wanted);
    }

    /**
     * The same order with the item at {@code from} moved to {@code to} — the drag, as arithmetic.
     *
     * A move that names a place outside the list, or that does not move anything, answers the same
     * order rather than throwing: a finger dragged off the edge of a row is not an error.
     */
    public DateOrder move(int from, int to) {
        if (from < 0 || to < 0 || from >= fields.size() || to >= fields.size() || from == to) {
            return this;
        }
        List<String> out = new ArrayList<String>(fields);
        out.add(to, out.remove(from));
        return new DateOrder(out);
    }
}
