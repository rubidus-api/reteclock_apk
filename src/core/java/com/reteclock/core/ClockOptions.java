package com.reteclock.core;

/**
 * User-selectable display options.
 *
 * Pure Java: no android.* imports. The Android layer maps these to SharedPreferences.
 */
public final class ClockOptions {

    /** Date written with an abbreviated month name: "Jul 12". */
    public static final int DATE_STYLE_NAME = 0;
    /** Date written numerically, month first: "07-12". */
    public static final int DATE_STYLE_NUMERIC = 1;

    /** Whether the seconds are shown at all. */
    public final boolean showSeconds;
    /** One of {@link #DATE_STYLE_NAME} or {@link #DATE_STYLE_NUMERIC}. */
    public final int dateStyle;

    public ClockOptions(boolean showSeconds, int dateStyle) {
        this.showSeconds = showSeconds;
        this.dateStyle = dateStyle == DATE_STYLE_NUMERIC ? DATE_STYLE_NUMERIC : DATE_STYLE_NAME;
    }

    /** Seconds shown, month written as "Jul 12". */
    public static ClockOptions defaults() {
        return new ClockOptions(true, DATE_STYLE_NAME);
    }
}
