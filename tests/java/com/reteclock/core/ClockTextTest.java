package com.reteclock.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** T001: clock strings for the wide and tall layouts. */
class ClockTextTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static long utc(int year, int month, int day, int hour, int minute, int second) {
        java.util.Calendar c = java.util.Calendar.getInstance(UTC);
        c.clear();
        c.set(year, month - 1, day, hour, minute, second);
        return c.getTimeInMillis();
    }

    private static ClockText at(int year, int month, int day, int h, int m, int s, ClockOptions o) {
        return ClockText.of(utc(year, month, day, h, m, s), UTC, o);
    }

    @Test
    void formatsEveryFieldOfTheSampleInstant() {
        ClockText t = at(2026, 7, 10, 13, 45, 25, ClockOptions.defaults());
        assertEquals("13", t.hour);
        assertEquals("45", t.minute);
        assertEquals("25", t.second);
        assertEquals("25s", t.secondLabel);
        assertEquals("Fri", t.weekday);
        assertEquals("Jul 10", t.monthDay);
        assertEquals("2026", t.year);
        assertEquals("Fri, Jul 10", t.weekdayDate);
        assertEquals("13:45", t.hourMinute);
        assertEquals("2026   25s", t.smallLine);
    }

    @Test
    void everyMonthIsAThreeLetterAbbreviation() {
        String[] expected = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                             "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        for (int month = 1; month <= 12; month++) {
            ClockText t = at(2026, month, 5, 0, 0, 0, ClockOptions.defaults());
            assertEquals(expected[month - 1] + " 5", t.monthDay);
            assertEquals(3, expected[month - 1].length());
        }
    }

    @Test
    void numericDateStyleWritesMonthFirstAndZeroPadded() {
        ClockOptions numeric = new ClockOptions(true, ClockOptions.DATE_STYLE_NUMERIC);
        ClockText t = at(2026, 7, 12, 9, 5, 0, numeric);
        assertEquals("07-12", t.monthDay);
        assertEquals("Sun, 07-12", t.weekdayDate);

        ClockText december = at(2026, 12, 31, 9, 5, 0, numeric);
        assertEquals("12-31", december.monthDay);
    }

    @Test
    void hidingTheSecondsRemovesThemFromTheSmallLine() {
        ClockOptions noSeconds = new ClockOptions(false, ClockOptions.DATE_STYLE_NAME);
        ClockText t = at(2026, 7, 12, 13, 45, 25, noSeconds);
        assertEquals("2026", t.smallLine);
        assertEquals("25s", t.secondLabel, "the string still exists; the layout decides to skip it");
    }

    @Test
    void usesTwentyFourHourClockWithZeroPadding() {
        ClockText midnight = at(2026, 1, 1, 0, 5, 9, ClockOptions.defaults());
        assertEquals("00", midnight.hour);
        assertEquals("05", midnight.minute);
        assertEquals("09", midnight.second);

        ClockText evening = at(2026, 12, 31, 23, 59, 59, ClockOptions.defaults());
        assertEquals("23", evening.hour);
        assertEquals("59", evening.minute);
        assertEquals("Dec 31", evening.monthDay);
    }

    @Test
    void weekdayAndMonthNamesStayEnglishRegardlessOfDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.KOREA);
            ClockText t = at(2026, 7, 12, 8, 0, 0, ClockOptions.defaults());
            assertEquals("Sun", t.weekday);
            assertEquals("Jul 12", t.monthDay);
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void redrawTicksAlignToTheSecondBoundary() {
        assertEquals(1000L, ClockText.millisToNextSecond(utc(2026, 7, 10, 13, 45, 25)));
        assertEquals(750L, ClockText.millisToNextSecond(utc(2026, 7, 10, 13, 45, 25) + 250L));
        assertTrue(ClockText.millisToNextSecond(utc(2026, 7, 10, 13, 45, 25) + 999L) == 1L);
    }
}
