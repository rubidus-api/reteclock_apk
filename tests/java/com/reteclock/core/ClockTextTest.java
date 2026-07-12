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

    @Test
    void formatsEveryFieldOfTheSampleInstant() {
        ClockText t = ClockText.of(utc(2026, 7, 10, 13, 45, 25), UTC);
        assertEquals("13", t.hour);
        assertEquals("45", t.minute);
        assertEquals("25", t.second);
        assertEquals("25s", t.secondLabel);
        assertEquals("Fri", t.weekday);
        assertEquals("July 10", t.monthDay);
        assertEquals("2026", t.year);
        assertEquals("Fri, July 10", t.weekdayDate);
        assertEquals("13:45", t.hourMinute);
        assertTrue(t.smallLine.startsWith("2026"));
        assertTrue(t.smallLine.endsWith("25s"));
    }

    @Test
    void usesTwentyFourHourClockWithZeroPadding() {
        ClockText midnight = ClockText.of(utc(2026, 1, 1, 0, 5, 9), UTC);
        assertEquals("00", midnight.hour);
        assertEquals("05", midnight.minute);
        assertEquals("09", midnight.second);

        ClockText evening = ClockText.of(utc(2026, 12, 31, 23, 59, 59), UTC);
        assertEquals("23", evening.hour);
        assertEquals("59", evening.minute);
        assertEquals("December 31", evening.monthDay);
    }

    @Test
    void weekdayNamesStayEnglishRegardlessOfDefaultLocale() {
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.KOREA);
            ClockText t = ClockText.of(utc(2026, 7, 12, 8, 0, 0), UTC);
            assertEquals("Sun", t.weekday);
            assertEquals("July 12", t.monthDay);
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    @Test
    void redrawTicksAlignToTheSecondBoundary() {
        assertEquals(1000L, ClockText.millisToNextSecond(utc(2026, 7, 10, 13, 45, 25)));
        assertEquals(750L, ClockText.millisToNextSecond(utc(2026, 7, 10, 13, 45, 25) + 250L));
        assertEquals(1L, ClockText.millisToNextSecond(utc(2026, 7, 10, 13, 45, 25) + 999L));
    }
}
