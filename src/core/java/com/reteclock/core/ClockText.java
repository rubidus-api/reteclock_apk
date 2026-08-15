package com.reteclock.core;

import java.util.TimeZone;

/**
 * Formatted clock strings for one instant.
 *
 * Pure Java: no android.* imports, so it can be unit tested on a plain JVM — and, since RFC-0004, no
 * `java.util.Calendar` either. The instant and an offset in minutes are all this needs; the date
 * comes from {@link CivilTime} and whichever calendar {@link ClockOptions#calendarSystem} names.
 *
 * Weekday names are fixed English abbreviations on purpose: the display must not change with the
 * device locale, and every calendar here rides the same seven-day week. Month names come from the
 * calendar in force, in Latin letters and ordinary digits throughout (RFC-0003, D5).
 */
public final class ClockText {

    /** Package-visible so {@link ClockSamples} can offer exactly the strings this produces. */
    static final String[] WEEKDAYS = {
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };

    /** As {@link #WEEKDAYS}. The Gregorian names; other calendars bring their own. */
    static final String[] MONTHS = {
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    };

    /** Two-digit hour, 24-hour clock: "00".."23". */
    public final String hour;
    /** Two-digit minute: "00".."59". */
    public final String minute;
    /** Two-digit second: "00".."59". */
    public final String second;
    /** Second with unit suffix, as shown in the wide layout: "25s". */
    public final String secondLabel;
    /** Short weekday name: "Sun". */
    public final String weekday;
    /** Month and day in the selected style: "Jul 12", "07-12", "Mor 24". */
    public final String monthDay;
    /** The year as its calendar writes it: "2026", "1405", "Reiwa 8". */
    public final String year;
    /** Weekday joined with the date, as shown in the tall layout: "Sun, Jul 12". */
    public final String weekdayDate;
    /** Small bottom line of the tall layout: the year, plus the seconds when they are shown. */
    public final String smallLine;
    /** Hour and minute joined, as shown in the wide layout: "13:45", or "1:45" on a 12-hour clock. */
    public final String hourMinute;
    /**
     * "AM" or "PM" on a twelve-hour clock, and null on a twenty-four hour one.
     *
     * Kept out of the hour and minute strings on purpose: it is drawn smaller and somewhere else —
     * tucked under the end of "7:19" where the two are on one line, and stacked above the hour where
     * they are not (issue #24). A field's own text is what gets sized and centred in its cell, and
     * the marker is neither.
     */
    public final String meridiem;
    /**
     * The Gregorian month and day, `08.15`, for the small inverted badge — or null.
     *
     * Null whenever it would say nothing: when the badge is switched off, and when the calendar in
     * force *is* the Gregorian one, where it would repeat the date it sits under.
     */
    public final String gregorianBadge;
    /** The day this text was made from, which the calendar grid compares against. */
    public final int jdn;

    private ClockText(CivilTime time, ClockOptions options) {
        jdn = time.jdn;
        // Midnight is twelve in the morning and noon is twelve at midday: the only two hours a
        // twelve-hour clock cannot get by dividing.
        if (!options.hour12) {
            hour = pad2(time.hour);
            meridiem = null;
        } else {
            boolean twelfth = time.hour % 12 == 0;
            boolean morning = time.hour < 12;
            if (!twelfth) {
                hour = Integer.toString(time.hour % 12);
                meridiem = morning ? "AM" : "PM";
            } else if (morning) {
                switch (options.midnightStyle) {
                    case ClockOptions.MIDNIGHT_PM:
                        hour = "12";
                        meridiem = "PM";
                        break;
                    case ClockOptions.MIDNIGHT_24H:
                        hour = pad2(time.hour);   // the one hour written the 24-hour way
                        meridiem = null;
                        break;
                    case ClockOptions.MIDNIGHT_MN:
                        hour = "12";
                        meridiem = "MN";
                        break;
                    case ClockOptions.MIDNIGHT_ZERO:
                        hour = "0";
                        meridiem = "AM";
                        break;
                    default:
                        hour = "12";
                        meridiem = "AM";
                        break;
                }
            } else {
                switch (options.noonStyle) {
                    case ClockOptions.NOON_AM:
                        hour = "12";
                        meridiem = "AM";
                        break;
                    case ClockOptions.NOON_NN:
                        hour = "12";
                        meridiem = "NN";
                        break;
                    case ClockOptions.NOON_ZERO:
                        hour = "0";
                        meridiem = "PM";
                        break;
                    default:
                        hour = "12";
                        meridiem = "PM";
                        break;
                }
            }
        }
        minute = pad2(time.minute);
        second = pad2(time.second);
        secondLabel = second + "s";
        weekday = Calendars.weekdayNames(options.calendarSystem, options.weekdayStyle)
                [CivilTime.weekday(time.jdn)];

        int shown = time.jdn;
        if (options.calendarSystem == Calendars.ISLAMIC) {
            shown += options.hijriOffsetDays;
        }
        CalendarDate date = Calendars.dateOf(options.calendarSystem, shown);
        monthDay = options.dateStyle == ClockOptions.DATE_STYLE_NUMERIC
                ? pad2(date.month) + "-" + pad2(date.day)
                : Calendars.monthName(date.system, date.year, date.month, options.nameStyle)
                        + " " + date.day;
        year = Calendars.yearText(options.calendarSystem, shown);
        weekdayDate = weekday + ", " + monthDay;
        smallLine = options.showSeconds ? year + "   " + secondLabel : year;
        hourMinute = hour + ":" + minute;

        int[] gregorian = Gregorian.parts(time.jdn);
        gregorianBadge = options.gregorianBadge && date.system != Calendars.GREGORIAN
                ? pad2(gregorian[1]) + "." + pad2(gregorian[2])
                : null;
    }

    /** Formats the given instant at the given offset east of UTC, in minutes. */
    public static ClockText at(long epochMillis, int offsetMinutes, ClockOptions options) {
        return new ClockText(CivilTime.of(epochMillis, offsetMinutes), options);
    }

    /** Formats the given instant in the given time zone, which is what the platform offers. */
    public static ClockText of(long epochMillis, TimeZone zone, ClockOptions options) {
        return at(epochMillis, zone.getOffset(epochMillis) / 60000, options);
    }

    /** Formats the given instant in the device default time zone. */
    public static ClockText of(long epochMillis, ClockOptions options) {
        return of(epochMillis, TimeZone.getDefault(), options);
    }

    /** Milliseconds until the next whole second, so redraws stay aligned to the clock. */
    public static long millisToNextSecond(long epochMillis) {
        long remainder = epochMillis % 1000L;
        if (remainder < 0L) {
            remainder += 1000L;
        }
        return 1000L - remainder;
    }

    private static String pad2(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
