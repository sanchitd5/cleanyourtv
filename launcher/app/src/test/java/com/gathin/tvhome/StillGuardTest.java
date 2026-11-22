package com.gathin.tvhome;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class StillGuardTest {
    private static final long[] SEEDS = {0L, 1L, 42L, -7L, 123456789012345L};

    @Test public void spotsStayInsideTheMargin() {
        for (long seed : SEEDS) {
            for (int n = 0; n < 1000; n++) {
                float[] p = StillGuard.spot(n, seed);
                assertTrue(p[0] >= 0.08f && p[0] <= 0.92f);
                assertTrue(p[1] >= 0.08f && p[1] <= 0.92f);
            }
        }
    }

    @Test public void everyBlockOfTwelveVisitsEveryCellOnce() {
        for (long seed : SEEDS) {
            for (int block = 0; block < 20; block++) {
                Set<Integer> cells = new HashSet<>();
                for (int i = 0; i < 12; i++) cells.add(StillGuard.cell(block * 12 + i, seed));
                assertEquals("seed " + seed + " block " + block, 12, cells.size());
            }
        }
    }

    @Test public void noTwoConsecutiveSpotsShareACellEvenAcrossBlocks() {
        for (long seed : SEEDS) {
            for (int n = 1; n < 1200; n++) {
                assertNotEquals("seed " + seed + " step " + n, StillGuard.cell(n - 1, seed), StillGuard.cell(n, seed));
            }
        }
    }

    @Test public void theScheduleIsDeterministicPerSeed() {
        for (int n = 0; n < 50; n++) assertArrayEquals(StillGuard.spot(n, 9L), StillGuard.spot(n, 9L), 0f);
        boolean differs = false;
        for (int n = 0; n < 12; n++) differs |= StillGuard.cell(n, 1L) != StillGuard.cell(n, 2L);
        assertTrue(differs);
    }

    @Test public void aNegativeStepIsTheFirst() {
        assertArrayEquals(StillGuard.spot(0, 5L), StillGuard.spot(-3, 5L), 0f);
    }

    @Test public void theOrbitHasEightPointsWithinTheEllipse() {
        Set<String> seen = new HashSet<>();
        for (int n = 0; n < 8; n++) {
            float[] o = StillGuard.orbit(n);
            assertTrue(Math.abs(o[0]) <= 6.0001f && Math.abs(o[1]) <= 4.0001f);
            seen.add(o[0] + "," + o[1]);
        }
        assertEquals(8, seen.size());
        assertEquals(6f, StillGuard.orbit(0)[0], 1e-5f);
        assertEquals(0f, StillGuard.orbit(0)[1], 1e-5f);
        assertEquals(4f, StillGuard.orbit(2)[1], 1e-5f);
        for (int n = 0; n < 20; n++) assertArrayEquals(StillGuard.orbit(n), StillGuard.orbit(n + 8), 1e-4f);
    }

    @Test public void dimIsOneUntilItStartsAndTheFloorAtTheEnd() {
        long after = 5 * 60_000L, ramp = 600_000L;
        assertEquals(1f, StillGuard.dim(0, after, ramp, 0.3f), 0f);
        assertEquals(1f, StillGuard.dim(after, after, ramp, 0.3f), 1e-6f);
        assertEquals(0.3f, StillGuard.dim(after + ramp, after, ramp, 0.3f), 1e-6f);
        assertEquals(0.3f, StillGuard.dim(after + 10 * ramp, after, ramp, 0.3f), 1e-6f);
        assertEquals(0.65f, StillGuard.dim(after + ramp / 2, after, ramp, 0.3f), 1e-6f);
    }

    @Test public void dimNeverIncreases() {
        float last = 1f;
        for (long t = 0; t < 20 * 60_000L; t += 5_000L) {
            float f = StillGuard.dim(t, 5 * 60_000L, 600_000L, 0.35f);
            assertTrue(f <= last + 1e-6f);
            last = f;
        }
    }

    @Test public void dimIsOffWhenAfterIsZero() {
        assertEquals(1f, StillGuard.dim(99_999_999L, 0, 600_000L, 0.1f), 0f);
        assertEquals(1f, StillGuard.dim(99_999_999L, -1, 600_000L, 0.1f), 0f);
    }

    @Test public void dimWithNoRampIsAStep() {
        assertEquals(1f, StillGuard.dim(99, 100, 0, 0.5f), 0f);
        assertEquals(0.5f, StillGuard.dim(100, 100, 0, 0.5f), 0f);
    }

    @Test public void blankHappensOnScheduleAndNeverWhenOff() {
        assertFalse(StillGuard.blank(29 * 60_000L, 30 * 60_000L));
        assertTrue(StillGuard.blank(30 * 60_000L, 30 * 60_000L));
        assertTrue(StillGuard.blank(31 * 60_000L, 30 * 60_000L));
        assertFalse(StillGuard.blank(Long.MAX_VALUE, 0));
        assertFalse(StillGuard.blank(Long.MAX_VALUE, -1));
    }

    @Test public void homeStagesComeInOrderAndZeroIsOff() {
        long calm = 20_000L, dim = 90_000L;
        assertEquals(0, StillGuard.homeStage(19_999L, calm, dim));
        assertEquals(StillGuard.CALM, StillGuard.homeStage(20_000L, calm, dim));
        assertEquals(StillGuard.CALM, StillGuard.homeStage(89_999L, calm, dim));
        assertEquals(StillGuard.CALM | StillGuard.DIM, StillGuard.homeStage(90_000L, calm, dim));
        assertEquals(StillGuard.DIM, StillGuard.homeStage(90_000L, 0, dim));
        assertEquals(StillGuard.CALM, StillGuard.homeStage(90_000L, calm, 0));
        assertEquals(0, StillGuard.homeStage(Long.MAX_VALUE, 0, 0));
    }

    @Test public void orbitStepStartsAtAMinuteAndAdvancesEveryMinute() {
        assertEquals(-1, StillGuard.orbitStep(0));
        assertEquals(-1, StillGuard.orbitStep(59_999L));
        assertEquals(0, StillGuard.orbitStep(60_000L));
        assertEquals(0, StillGuard.orbitStep(119_999L));
        assertEquals(1, StillGuard.orbitStep(120_000L));
        assertEquals(10, StillGuard.orbitStep(660_000L));
    }

    @Test public void theCaptionAlternatesSides() {
        assertTrue(StillGuard.captionAtStart(0));
        assertFalse(StillGuard.captionAtStart(1));
        assertTrue(StillGuard.captionAtStart(2));
        assertFalse(StillGuard.captionAtStart(7));
    }
}
