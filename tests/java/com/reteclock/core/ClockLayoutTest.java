package com.reteclock.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T003: layout geometry for wide and tall screens. */
class ClockLayoutTest {

    private static final ClockOptions WITH_SECONDS = ClockOptions.defaults();
    private static final ClockOptions NO_SECONDS =
            new ClockOptions(false, ClockOptions.DATE_STYLE_NAME);

    private static ClockLayout.Slot slot(ClockLayout layout, String role) {
        for (ClockLayout.Slot s : layout.slots()) {
            if (s.role.equals(role)) {
                return s;
            }
        }
        return null;
    }

    private static List<String> roles(ClockLayout layout) {
        List<String> out = new ArrayList<String>();
        for (ClockLayout.Slot s : layout.slots()) {
            out.add(s.role);
        }
        return out;
    }

    /** No slot may reach outside the screen, and no two slots may overlap vertically. */
    private static void assertFitsAndDoesNotOverlap(ClockLayout layout, int w, int h) {
        ClockLayout.Slot previous = null;
        for (ClockLayout.Slot s : layout.slots()) {
            float top = s.centerY - s.textSize / 2f;
            float bottom = s.centerY + s.textSize / 2f;
            assertTrue(top >= 0f, s.role + " stays below the top edge");
            assertTrue(bottom <= h, s.role + " stays above the bottom edge");
            assertTrue(s.maxWidth > 0f && s.maxWidth <= w, s.role + " has a sane box width");
            if (previous != null && previous.centerX == s.centerX) {
                float previousBottom = previous.centerY + previous.textSize / 2f;
                assertTrue(previousBottom <= top + 0.001f,
                        s.role + " does not overlap " + previous.role);
            }
            previous = s;
        }
    }

    @Test
    void wideScreenPutsTheBigTimeLeftAndTheDetailsRight() {
        int w = 1280;
        int h = 720;
        ClockLayout layout = ClockLayout.of(w, h, WITH_SECONDS);
        assertTrue(layout.isWide());
        assertEquals(List.of(
                ClockLayout.ROLE_HOUR_MINUTE,
                ClockLayout.ROLE_SECOND,
                ClockLayout.ROLE_WEEKDAY,
                ClockLayout.ROLE_MONTH_DAY,
                ClockLayout.ROLE_YEAR), roles(layout));

        ClockLayout.Slot main = slot(layout, ClockLayout.ROLE_HOUR_MINUTE);
        assertNotNull(main);
        assertTrue(main.bold, "the big time is bold");
        assertTrue(main.centerX < w / 2f, "the big time sits in the left half");
        assertTrue(main.textSize > h * 0.85f, "the big time takes the height it can get");

        float previousY = -1f;
        for (String role : new String[] {ClockLayout.ROLE_SECOND, ClockLayout.ROLE_WEEKDAY,
                                         ClockLayout.ROLE_MONTH_DAY, ClockLayout.ROLE_YEAR}) {
            ClockLayout.Slot s = slot(layout, role);
            assertNotNull(s, role);
            assertFalse(s.bold, role + " is not bold");
            assertTrue(s.centerX > main.centerX, role + " is right of the big time");
            assertTrue(s.centerY > previousY, role + " is below the previous line");
            assertTrue(s.textSize < main.textSize, role + " is smaller than the big time");
            previousY = s.centerY;
        }

        // The right column fits and stays centered on the screen.
        ClockLayout.Slot first = slot(layout, ClockLayout.ROLE_SECOND);
        ClockLayout.Slot last = slot(layout, ClockLayout.ROLE_YEAR);
        float top = first.centerY - first.textSize / 2f;
        float bottom = last.centerY + last.textSize / 2f;
        assertTrue(top > 0f && bottom < h, "the column fits on screen");
        assertEquals(h / 2f, (top + bottom) / 2f, 1f, "the column is centered vertically");
    }

    @Test
    void wideScreenDropsTheSecondsLineWhenTheUserTurnsSecondsOff() {
        ClockLayout layout = ClockLayout.of(1280, 720, NO_SECONDS);
        assertNull(slot(layout, ClockLayout.ROLE_SECOND));
        assertEquals(List.of(
                ClockLayout.ROLE_HOUR_MINUTE,
                ClockLayout.ROLE_WEEKDAY,
                ClockLayout.ROLE_MONTH_DAY,
                ClockLayout.ROLE_YEAR), roles(layout));
    }

    @Test
    void tallScreenStacksHourMinuteDateAndSmallLine() {
        int w = 480;
        int h = 800;
        ClockLayout layout = ClockLayout.of(w, h, WITH_SECONDS);
        assertFalse(layout.isWide());
        assertEquals(List.of(
                ClockLayout.ROLE_HOUR,
                ClockLayout.ROLE_MINUTE,
                ClockLayout.ROLE_WEEKDAY_DATE,
                ClockLayout.ROLE_SMALL_LINE), roles(layout));

        ClockLayout.Slot hour = slot(layout, ClockLayout.ROLE_HOUR);
        ClockLayout.Slot minute = slot(layout, ClockLayout.ROLE_MINUTE);
        ClockLayout.Slot date = slot(layout, ClockLayout.ROLE_WEEKDAY_DATE);
        ClockLayout.Slot small = slot(layout, ClockLayout.ROLE_SMALL_LINE);

        assertTrue(hour.bold && minute.bold, "hour and minute are bold");
        assertFalse(date.bold || small.bold, "the smaller lines are not bold");
        assertEquals(hour.textSize, minute.textSize, 0.001f, "hour and minute are equally large");
        assertTrue(hour.textSize > h * 0.3f, "the hour takes the space the other lines leave");
        assertTrue(date.textSize < minute.textSize && small.textSize < date.textSize);

        for (ClockLayout.Slot s : layout.slots()) {
            assertEquals(w / 2f, s.centerX, 0.001f, s.role + " is horizontally centered");
        }
        assertFitsAndDoesNotOverlap(layout, w, h);
    }

    @Test
    void nothingIsClippedOrOverlappingOnAnyScreenShape() {
        int[][] screens = {
            {240, 320}, {320, 480}, {480, 800}, {720, 1280}, {1080, 1920}, {1440, 3200},
            {320, 240}, {480, 320}, {800, 480}, {1280, 720}, {1920, 1080}, {2560, 1600},
            {600, 600},
        };
        for (int[] screen : screens) {
            for (ClockOptions options : List.of(WITH_SECONDS, NO_SECONDS)) {
                ClockLayout layout = ClockLayout.of(screen[0], screen[1], options);
                assertFitsAndDoesNotOverlap(layout, screen[0], screen[1]);
                for (ClockLayout.Slot s : layout.slots()) {
                    assertTrue(s.textSize > 0f, s.role + " has a positive size");
                }
            }
        }
    }

    @Test
    void hidingTheSecondsGivesTheHourAndMinuteAtLeastAsMuchRoom() {
        ClockLayout with = ClockLayout.of(1280, 720, WITH_SECONDS);
        ClockLayout without = ClockLayout.of(1280, 720, NO_SECONDS);
        assertTrue(slot(without, ClockLayout.ROLE_HOUR_MINUTE).textSize
                >= slot(with, ClockLayout.ROLE_HOUR_MINUTE).textSize);
    }

    @Test
    void squareScreenUsesTheTallLayout() {
        assertFalse(ClockLayout.of(600, 600, WITH_SECONDS).isWide());
    }

    @Test
    void shrinkToFitScalesOversizedTextDownAndLeavesFittingTextAlone() {
        assertEquals(100f, ClockLayout.shrinkToFit(100f, 300f, 400f), 0.001f);
        assertEquals(50f, ClockLayout.shrinkToFit(100f, 800f, 400f), 0.001f);
        assertEquals(100f, ClockLayout.shrinkToFit(100f, 0f, 400f), 0.001f);
    }
}
