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

    /** What the time has always taken of a wide screen's width. */
    public static final float DEFAULT_TIME_FRACTION_WIDE = 0.62f;
    /** What the time has always taken of a tall screen's content height, near enough. */
    public static final float DEFAULT_TIME_FRACTION_TALL = 0.85f;
    /** The dial's ends: outside these a share stops being a layout and starts being a bug. */
    public static final float MIN_TIME_FRACTION = 0.2f;
    public static final float MAX_TIME_FRACTION = 0.9f;

    /** Whether the seconds are shown at all. */
    public final boolean showSeconds;
    /** One of {@link #DATE_STYLE_NAME} or {@link #DATE_STYLE_NUMERIC}. */
    public final int dateStyle;
    /** The share of a wide screen's width the hour and minute take; the rest is the side column. */
    public final float timeFractionWide;
    /** The share of a tall screen's content height the hour and minute take. */
    public final float timeFractionTall;

    public ClockOptions(boolean showSeconds, int dateStyle) {
        this(showSeconds, dateStyle, DEFAULT_TIME_FRACTION_WIDE, DEFAULT_TIME_FRACTION_TALL);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall) {
        this.showSeconds = showSeconds;
        this.dateStyle = dateStyle == DATE_STYLE_NUMERIC ? DATE_STYLE_NUMERIC : DATE_STYLE_NAME;
        this.timeFractionWide = clampFraction(timeFractionWide);
        this.timeFractionTall = clampFraction(timeFractionTall);
    }

    private static float clampFraction(float fraction) {
        return Math.max(MIN_TIME_FRACTION, Math.min(MAX_TIME_FRACTION, fraction));
    }

    /** Seconds shown, month written as "Jul 12", the time's share as it has always been. */
    public static ClockOptions defaults() {
        return new ClockOptions(true, DATE_STYLE_NAME);
    }
}
