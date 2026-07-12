package com.reteclock.core;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Formatted clock strings for one instant.
 *
 * Pure Java: no android.* imports, so it can be unit tested on a plain JVM.
 * Month and weekday names are fixed English abbreviations on purpose; the display must not
 * change with the device locale.
 */
public final class ClockText {

    private static final String[] WEEKDAYS = {
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
    };

    private static final String[] MONTHS = {
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
    /** Month and day in the selected style: "Jul 12" or "07-12". */
    public final String monthDay;
    /** Four-digit year: "2026". */
    public final String year;
    /** Weekday joined with the date, as shown in the tall layout: "Sun, Jul 12". */
    public final String weekdayDate;
    /** Small bottom line of the tall layout: the year, plus the seconds when they are shown. */
    public final String smallLine;
    /** Hour and minute joined, as shown in the wide layout: "13:45". */
    public final String hourMinute;

    private ClockText(Calendar c, ClockOptions options) {
        hour = pad2(c.get(Calendar.HOUR_OF_DAY));
        minute = pad2(c.get(Calendar.MINUTE));
        second = pad2(c.get(Calendar.SECOND));
        secondLabel = second + "s";
        weekday = WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1];
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        monthDay = options.dateStyle == ClockOptions.DATE_STYLE_NUMERIC
                ? pad2(month + 1) + "-" + pad2(day)
                : MONTHS[month] + " " + day;
        year = Integer.toString(c.get(Calendar.YEAR));
        weekdayDate = weekday + ", " + monthDay;
        smallLine = options.showSeconds ? year + "   " + secondLabel : year;
        hourMinute = hour + ":" + minute;
    }

    /** Formats the given instant in the given time zone. */
    public static ClockText of(long epochMillis, TimeZone zone, ClockOptions options) {
        Calendar c = Calendar.getInstance(zone);
        c.setTimeInMillis(epochMillis);
        return new ClockText(c, options);
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
