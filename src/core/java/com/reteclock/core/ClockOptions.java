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
    /** Whether a month's grid is shown under the time. */
    public final boolean calendar;
    /** Whether a saying is shown in a thin strip along the bottom. */
    public final boolean quote;
    /** Which calendar the dates are counted in — one of the constants in {@link Calendars}. */
    public final int calendarSystem;
    /** Whether the Gregorian month and day are shown as a small inverted badge (RFC-0003, D14). */
    public final boolean gregorianBadge;
    /** What the user shifted the Islamic date by, to match their own community: -2..+2 days. */
    public final int hijriOffsetDays;
    /** Which spelling of the month names, where the calendar has more than one in use. */
    public final int nameStyle;
    /** Whether the weekdays are drawn in English or in the calendar's own names. */
    public final int weekdayStyle;
    /** Whether the time reads 1 to 12 with AM or PM, rather than 0 to 23 (issue #24). */
    public final boolean hour12;
    /**
     * What a twelve-hour clock does at noon and at midnight, which is the one thing it cannot say
     * plainly.
     *
     * AM means *before* midday and PM means *after* it, so noon is neither: it is midday itself.
     * NIST goes as far as "12 a.m. and 12 p.m. are ambiguous and should not be used". Every phone
     * uses them anyway, and so does this clock by default — but the two conventions that resolve the
     * ambiguity are here as well, because both are in real use.
     */
    public final int noonStyle;
    /** And what it does at midnight, which is the same question asked at the other end of the day. */
    public final int midnightStyle;
    /**
     * The months and weekdays the user renamed, if any.
     *
     * A style says which published spelling to use; this says what the user typed, and it wins over
     * the style wherever they typed anything. Never null — {@link CustomNames#NONE} means they did
     * not.
     */
    public final CustomNames names;

    /** Noon reads `12 PM` — what nearly every clock does. */
    public static final int NOON_PM = 0;
    /** Or `12 AM`, which some readers expect and which the abbreviation can be argued into. */
    public static final int NOON_AM = 1;
    /** Or `12 NN`, the Philippine way, which cannot be misread. */
    public static final int NOON_NN = 2;
    /** Or `0 PM`, the Japanese way, where the twelfth hour is written zero. */
    public static final int NOON_ZERO = 3;

    /** Midnight reads `12 AM` — the usual convention. */
    public static final int MIDNIGHT_AM = 0;
    /** Or `12 PM`, taking midnight as the end of the day rather than the start of one. */
    public static final int MIDNIGHT_PM = 1;
    /**
     * Or `00:00` — that one hour written the twenty-four hour way, with no marker at all.
     *
     * It is the odd one out, and deliberately kept: it is the only option that removes the
     * ambiguity rather than choosing a side in it, and writing midnight as 00:00 while speaking of
     * the rest of the day in twelve hours is exactly what a good deal of Europe and East Asia
     * actually does.
     */
    public static final int MIDNIGHT_24H = 2;
    /** Or `12 MN`, the Philippine way. */
    public static final int MIDNIGHT_MN = 3;
    /** Or `0 AM`, the Japanese way. */
    public static final int MIDNIGHT_ZERO = 4;

    /** The same options with the calendar switched on or off. */
    public ClockOptions withCalendar(boolean showCalendar) {
        return new ClockOptions(showSeconds, dateStyle, timeFractionWide, timeFractionTall,
                showCalendar, quote, calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle,
                weekdayStyle, hour12, noonStyle, midnightStyle, names);
    }

    /** The same options counting in another calendar. */
    public ClockOptions withCalendarSystem(int system, boolean badge, int hijriOffset) {
        return new ClockOptions(showSeconds, dateStyle, timeFractionWide, timeFractionTall,
                calendar, quote, system, badge, hijriOffset, nameStyle, weekdayStyle, hour12,
                noonStyle, midnightStyle, names);
    }

    /** The same options with the months spelled another way. */
    public ClockOptions withNameStyle(int style) {
        return new ClockOptions(showSeconds, dateStyle, timeFractionWide, timeFractionTall,
                calendar, quote, calendarSystem, gregorianBadge, hijriOffsetDays, style,
                weekdayStyle, hour12, noonStyle, midnightStyle, names);
    }

    /** The same options with the weekdays named the other way. */
    public ClockOptions withWeekdayStyle(int style) {
        return new ClockOptions(showSeconds, dateStyle, timeFractionWide, timeFractionTall,
                calendar, quote, calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle,
                style, hour12, noonStyle, midnightStyle, names);
    }

    /** The same options on a twelve-hour clock, or back on a twenty-four hour one. */
    public ClockOptions withHour12(boolean twelve) {
        return withHour12(twelve, noonStyle, midnightStyle);
    }

    /** And with ways of writing noon and midnight. */
    public ClockOptions withHour12(boolean twelve, int noon, int midnight) {
        return new ClockOptions(showSeconds, dateStyle, timeFractionWide, timeFractionTall,
                calendar, quote, calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle,
                weekdayStyle, twelve, noon, midnight, names);
    }

    /** The same options with the user's own month and weekday names. */
    public ClockOptions withNames(CustomNames names) {
        return new ClockOptions(showSeconds, dateStyle, timeFractionWide, timeFractionTall,
                calendar, quote, calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle,
                weekdayStyle, hour12, noonStyle, midnightStyle, names);
    }

    public ClockOptions(boolean showSeconds, int dateStyle) {
        this(showSeconds, dateStyle, DEFAULT_TIME_FRACTION_WIDE, DEFAULT_TIME_FRACTION_TALL);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, false);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, false);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, quote,
                Calendars.GREGORIAN, false, 0);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote, int calendarSystem,
            boolean gregorianBadge, int hijriOffsetDays) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, quote,
                calendarSystem, gregorianBadge, hijriOffsetDays, 0);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote, int calendarSystem,
            boolean gregorianBadge, int hijriOffsetDays, int nameStyle) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, quote,
                calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle, 0);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote, int calendarSystem,
            boolean gregorianBadge, int hijriOffsetDays, int nameStyle, int weekdayStyle) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, quote,
                calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle, weekdayStyle, false);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote, int calendarSystem,
            boolean gregorianBadge, int hijriOffsetDays, int nameStyle, int weekdayStyle,
            boolean hour12) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, quote,
                calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle, weekdayStyle, hour12,
                NOON_PM, MIDNIGHT_AM);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote, int calendarSystem,
            boolean gregorianBadge, int hijriOffsetDays, int nameStyle, int weekdayStyle,
            boolean hour12, int noonStyle, int midnightStyle) {
        this(showSeconds, dateStyle, timeFractionWide, timeFractionTall, calendar, quote,
                calendarSystem, gregorianBadge, hijriOffsetDays, nameStyle, weekdayStyle, hour12,
                noonStyle, midnightStyle, CustomNames.NONE);
    }

    public ClockOptions(boolean showSeconds, int dateStyle, float timeFractionWide,
            float timeFractionTall, boolean calendar, boolean quote, int calendarSystem,
            boolean gregorianBadge, int hijriOffsetDays, int nameStyle, int weekdayStyle,
            boolean hour12, int noonStyle, int midnightStyle, CustomNames names) {
        this.names = names == null ? CustomNames.NONE : names;
        this.noonStyle = noonStyle < 0 || noonStyle > NOON_ZERO ? NOON_PM : noonStyle;
        this.midnightStyle = midnightStyle < 0 || midnightStyle > MIDNIGHT_ZERO
                ? MIDNIGHT_AM : midnightStyle;   // a stored 4 from the build that had five falls back
        this.hour12 = hour12;
        this.weekdayStyle = weekdayStyle < 0 ? 0 : weekdayStyle;
        this.nameStyle = nameStyle < 0 ? 0 : nameStyle;
        this.calendarSystem = calendarSystem >= 0 && calendarSystem < Calendars.COUNT
                ? calendarSystem : Calendars.GREGORIAN;
        this.gregorianBadge = gregorianBadge;
        this.hijriOffsetDays = hijriOffsetDays < -2 ? -2 : hijriOffsetDays > 2 ? 2 : hijriOffsetDays;
        this.quote = quote;
        this.calendar = calendar;
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
