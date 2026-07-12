package com.reteclock.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T003: layout geometry for wide and tall screens. */
class ClockLayoutTest {

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

    @Test
    void wideScreenPutsTheBigTimeLeftAndFourLinesRight() {
        ClockLayout layout = ClockLayout.of(1280, 720);
        assertTrue(layout.isWide());
        assertEquals(5, layout.slots().size());
        assertEquals(List.of(
                ClockLayout.ROLE_HOUR_MINUTE,
                ClockLayout.ROLE_SECOND,
                ClockLayout.ROLE_WEEKDAY,
                ClockLayout.ROLE_MONTH_DAY,
                ClockLayout.ROLE_YEAR), roles(layout));

        ClockLayout.Slot main = slot(layout, ClockLayout.ROLE_HOUR_MINUTE);
        assertNotNull(main);
        assertTrue(main.centerX < 1280 / 2f, "big time sits in the left half");
        assertTrue(main.textSize > 720 * 0.4f, "big time is large");

        // The right column is ordered top to bottom and stays right of the big time.
        float previousY = -1f;
        String[] column = {
            ClockLayout.ROLE_SECOND,
            ClockLayout.ROLE_WEEKDAY,
            ClockLayout.ROLE_MONTH_DAY,
            ClockLayout.ROLE_YEAR
        };
        for (String role : column) {
            ClockLayout.Slot s = slot(layout, role);
            assertNotNull(s, role);
            assertTrue(s.centerX > main.centerX, role + " is right of the big time");
            assertTrue(s.centerY > previousY, role + " is below the previous line");
            assertTrue(s.textSize < main.textSize, role + " is smaller than the big time");
            previousY = s.centerY;
        }
    }

    @Test
    void wideRightColumnIsVerticallyCenteredAndInsideTheScreen() {
        int w = 800;
        int h = 480;
        ClockLayout layout = ClockLayout.of(w, h);
        ClockLayout.Slot first = slot(layout, ClockLayout.ROLE_SECOND);
        ClockLayout.Slot last = slot(layout, ClockLayout.ROLE_YEAR);
        float top = first.centerY - first.textSize / 2f;
        float bottom = last.centerY + last.textSize / 2f;
        assertTrue(top > 0f && bottom < h, "column fits on screen");
        assertEquals(h / 2f, (top + bottom) / 2f, 1f, "column is centered vertically");
    }

    @Test
    void tallScreenStacksHourMinuteDateAndSmallLine() {
        int w = 480;
        int h = 800;
        ClockLayout layout = ClockLayout.of(w, h);
        assertTrue(!layout.isWide());
        assertEquals(List.of(
                ClockLayout.ROLE_HOUR,
                ClockLayout.ROLE_MINUTE,
                ClockLayout.ROLE_WEEKDAY_DATE,
                ClockLayout.ROLE_SMALL_LINE), roles(layout));

        ClockLayout.Slot hour = slot(layout, ClockLayout.ROLE_HOUR);
        ClockLayout.Slot minute = slot(layout, ClockLayout.ROLE_MINUTE);
        ClockLayout.Slot date = slot(layout, ClockLayout.ROLE_WEEKDAY_DATE);
        ClockLayout.Slot small = slot(layout, ClockLayout.ROLE_SMALL_LINE);

        assertEquals(hour.textSize, minute.textSize, 0.001f, "hour and minute are equally large");
        assertTrue(hour.centerY < minute.centerY);
        assertTrue(minute.centerY < date.centerY);
        assertTrue(date.centerY < small.centerY);
        assertTrue(date.textSize < minute.textSize && small.textSize < date.textSize);

        for (ClockLayout.Slot s : layout.slots()) {
            assertEquals(w / 2f, s.centerX, 0.001f, s.role + " is horizontally centered");
            assertTrue(s.centerY - s.textSize / 2f > 0f, s.role + " is below the top edge");
            assertTrue(s.centerY + s.textSize / 2f < h, s.role + " is above the bottom edge");
        }
    }

    @Test
    void squareScreenUsesTheTallLayout() {
        assertTrue(!ClockLayout.of(600, 600).isWide());
    }

    @Test
    void slotsAreDimmerThanTheMainTimeButVisible() {
        for (ClockLayout layout : List.of(ClockLayout.of(1280, 720), ClockLayout.of(720, 1280))) {
            for (ClockLayout.Slot s : layout.slots()) {
                assertTrue(s.alpha >= 120 && s.alpha <= 255, s.role + " alpha in range");
            }
        }
    }

    @Test
    void shrinkToFitScalesOversizedTextDownAndLeavesFittingTextAlone() {
        assertEquals(100f, ClockLayout.shrinkToFit(100f, 300f, 400f), 0.001f);
        assertEquals(50f, ClockLayout.shrinkToFit(100f, 800f, 400f), 0.001f);
        assertEquals(100f, ClockLayout.shrinkToFit(100f, 0f, 400f), 0.001f);
    }
}
