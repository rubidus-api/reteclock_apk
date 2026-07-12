package com.reteclock.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T002: burn-in protection offsets. */
class BurnInShiftTest {

    @Test
    void offsetNeverLeavesTheAllowedRange() {
        int max = BurnInShift.maxShiftPx(1280, 720);
        for (long ms = 0L; ms < BurnInShift.STEP_MS * BurnInShift.STEPS * 3L; ms += 5000L) {
            assertTrue(Math.abs(BurnInShift.offsetX(ms, max)) <= max + 0.001f);
            assertTrue(Math.abs(BurnInShift.offsetY(ms, max)) <= max + 0.001f);
        }
    }

    @Test
    void amplitudeScalesWithTheShorterEdgeAndStaysBounded() {
        assertEquals(22, BurnInShift.maxShiftPx(1280, 720));
        assertEquals(4, BurnInShift.maxShiftPx(120, 80));
        assertEquals(32, BurnInShift.maxShiftPx(4000, 3000));
    }

    @Test
    void positionHoldsForOneStepThenMoves() {
        long inStep = BurnInShift.STEP_MS / 2L;
        assertEquals(0, BurnInShift.stepIndex(0L));
        assertEquals(0, BurnInShift.stepIndex(inStep));
        assertEquals(1, BurnInShift.stepIndex(BurnInShift.STEP_MS));
        assertEquals(1, BurnInShift.stepIndex(BurnInShift.STEP_MS + inStep));
    }

    @Test
    void everyStepOfACycleIsADistinctPositionAndTheCycleRepeats() {
        int max = 20;
        Set<String> positions = new HashSet<String>();
        for (int i = 0; i < BurnInShift.STEPS; i++) {
            long ms = i * BurnInShift.STEP_MS;
            positions.add(round(BurnInShift.offsetX(ms, max)) + "," + round(BurnInShift.offsetY(ms, max)));
        }
        assertEquals(BurnInShift.STEPS, positions.size());

        long cycle = BurnInShift.STEP_MS * BurnInShift.STEPS;
        assertEquals(BurnInShift.offsetX(0L, max), BurnInShift.offsetX(cycle, max), 0.001f);
        assertEquals(BurnInShift.offsetY(0L, max), BurnInShift.offsetY(cycle, max), 0.001f);
    }

    @Test
    void neighbouringStepsMoveOnlyASmallDistance() {
        int max = BurnInShift.maxShiftPx(1080, 1920);
        for (int i = 1; i < BurnInShift.STEPS; i++) {
            float dx = BurnInShift.offsetX(i * BurnInShift.STEP_MS, max)
                    - BurnInShift.offsetX((i - 1) * BurnInShift.STEP_MS, max);
            float dy = BurnInShift.offsetY(i * BurnInShift.STEP_MS, max)
                    - BurnInShift.offsetY((i - 1) * BurnInShift.STEP_MS, max);
            assertTrue(Math.sqrt(dx * dx + dy * dy) <= max);
        }
    }

    private static long round(float value) {
        return Math.round(value * 100.0f);
    }
}
