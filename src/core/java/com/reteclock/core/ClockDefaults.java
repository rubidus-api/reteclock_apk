package com.reteclock.core;

/**
 * What the per-field settings start out as, before the user has touched them.
 *
 * Kept apart from {@link ClockLayout} on purpose. The layout used to make the hour and the minute
 * bold and that was not a default at all — it was combined with the user's own toggle, so bold
 * could be switched on for those fields but never off. That is gone. What lives here is only a
 * starting point: the settings screen shows it checked, and unchecking it works like any other.
 *
 * Pure Java: no android.* imports.
 */
public final class ClockDefaults {

    private ClockDefaults() {
    }

    /**
     * Whether this field arrives bold on a fresh install.
     *
     * The hour and the minute do, because that is how the clock has looked since the beginning and
     * it is what makes the time read as the time at a glance. Everything else starts light.
     */
    /**
     * The field a later field was split out of, or null for one that was always its own.
     *
     * AM/PM was drawn with the hour's paint before it was a field; the calendar's three parts were
     * drawn in the date's. Somebody who set an outline on the date years ago expects the calendar
     * to keep it, and somebody who never set one expects the new fields to stay plain — inheriting
     * from the field it came out of gives both, where falling back to the app's old single
     * outline flag gave neither reliably.
     */
    public static String splitFrom(String role) {
        if (ClockLayout.ROLE_MERIDIEM.equals(role)) {
            return ClockLayout.ROLE_HOUR;
        }
        if (ClockLayout.ROLE_CALENDAR_TITLE.equals(role)
                || ClockLayout.ROLE_CALENDAR_WEEKDAY.equals(role)
                || ClockLayout.ROLE_CALENDAR_DAY.equals(role)) {
            return ClockLayout.ROLE_MONTH_DAY;
        }
        return null;
    }

    public static boolean boldByDefault(String role) {
        return ClockLayout.ROLE_HOUR.equals(role) || ClockLayout.ROLE_MINUTE.equals(role)
                // The marker belonged to the hour's paint before it had a field of its own, so it
                // was bold wherever the hour was. Keeping that as the default means upgrading does
                // not quietly change how anybody's clock reads.
                || ClockLayout.ROLE_MERIDIEM.equals(role);
    }
}
