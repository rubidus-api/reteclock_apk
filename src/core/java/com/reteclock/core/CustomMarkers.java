package com.reteclock.core;

/**
 * What the twelve-hour clock writes after the time, where the user would rather it wrote something
 * else.
 *
 * AM and PM are Latin abbreviations that much of the world does not use in Latin. Korea writes
 * 오전 and 오후, Japan 午前 and 午後, and plenty of people simply want a dot, an arrow or nothing at
 * all. The four markers here — before noon, after noon, noon itself and midnight — are free text,
 * with no length and no alphabet enforced, exactly like the month and weekday names
 * ({@link CustomNames}).
 *
 * Noon and midnight are separate from AM and PM on purpose. Which of them is written is settled by
 * the noon and midnight conventions the user picked, and this is a layer over that: set nothing and
 * the convention shows through, set something and it is written at that hour whatever the
 * convention chose. Leaving the midnight marker alone matters for the one convention that writes no
 * marker at all — the 24-hour form, `00:00`, which stays bare.
 *
 * Pure Java: no android.*, no java.util.*.
 */
public final class CustomMarkers {

    /** Nothing of the user's own: the built-in AM, PM, NN and MN show through. */
    public static final CustomMarkers NONE = new CustomMarkers("", "", "", "");

    private static final char SEPARATOR = '\t';

    private final String am;
    private final String pm;
    private final String noon;
    private final String midnight;

    private CustomMarkers(String am, String pm, String noon, String midnight) {
        this.am = clean(am);
        this.pm = clean(pm);
        this.noon = clean(noon);
        this.midnight = clean(midnight);
    }

    public static CustomMarkers of(String am, String pm, String noon, String midnight) {
        return new CustomMarkers(am, pm, noon, midnight);
    }

    /** Reads back what {@link #text} wrote. */
    public static CustomMarkers parse(String text) {
        if (text == null || text.length() == 0) {
            return NONE;
        }
        String[] parts = new String[4];
        int at = 0;
        int start = 0;
        for (int i = 0; i <= text.length() && at < 4; i++) {
            if (i == text.length() || text.charAt(i) == SEPARATOR) {
                parts[at++] = text.substring(start, i);
                start = i + 1;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (parts[i] == null) {
                parts[i] = "";
            }
        }
        return new CustomMarkers(parts[0], parts[1], parts[2], parts[3]);
    }

    public String text() {
        return isEmpty() ? "" : am + SEPARATOR + pm + SEPARATOR + noon + SEPARATOR + midnight;
    }

    public boolean isEmpty() {
        return am.length() == 0 && pm.length() == 0 && noon.length() == 0
                && midnight.length() == 0;
    }

    /** What the user typed, blank where they typed nothing: what a text field is filled with. */
    public String amEntry() {
        return am;
    }

    public String pmEntry() {
        return pm;
    }

    public String noonEntry() {
        return noon;
    }

    public String midnightEntry() {
        return midnight;
    }

    public CustomMarkers withAm(String value) {
        return new CustomMarkers(value, pm, noon, midnight);
    }

    public CustomMarkers withPm(String value) {
        return new CustomMarkers(am, value, noon, midnight);
    }

    public CustomMarkers withNoon(String value) {
        return new CustomMarkers(am, pm, value, midnight);
    }

    public CustomMarkers withMidnight(String value) {
        return new CustomMarkers(am, pm, noon, value);
    }

    /**
     * The marker to write for an ordinary hour.
     *
     * @param built what the convention chose — "AM" or "PM"
     */
    public String ordinary(String built) {
        return swap(built);
    }

    /**
     * The marker to write at noon or at midnight.
     *
     * The hour's own marker wins if the user set one; failing that the convention's choice goes
     * through the AM and PM replacements, so somebody who renamed PM sees their word at noon too
     * without having to say so twice. A convention that writes no marker keeps writing none.
     */
    public String atNoon(String built) {
        return noon.length() > 0 ? noon : swap(built);
    }

    public String atMidnight(String built) {
        if (built == null) {
            return null;                     // the 24-hour form: 00:00, and nothing after it
        }
        return midnight.length() > 0 ? midnight : swap(built);
    }

    private String swap(String built) {
        if (built == null) {
            return null;
        }
        if ("AM".equals(built) && am.length() > 0) {
            return am;
        }
        if ("PM".equals(built) && pm.length() > 0) {
            return pm;
        }
        return built;
    }

    private static String clean(String value) {
        // Tab separates the four, so it is the one character that cannot be part of one; a newline
        // would break the line the same way.
        return value == null ? "" : value.replace(SEPARATOR, ' ').replace('\n', ' ').trim();
    }
}
