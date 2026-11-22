package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShelfScrimTest {
    @Test public void theMiddleOfTheScreenIsNeverDarkened() {
        for (float y = 0f; y <= 0.42f; y += 0.01f) assertEquals("y=" + y, 0f, ShelfScrim.rampAlpha(y), 0f);
        assertEquals(0f, ShelfScrim.rampAlpha(-1f), 0f);
    }

    @Test public void theRampNeverDecreases() {
        float last = 0f;
        for (int i = 0; i <= 1000; i++) {
            float a = ShelfScrim.rampAlpha(i / 1000f);
            assertTrue("i=" + i, a >= last - 1e-6f);
            last = a;
        }
    }

    @Test public void theTitleAndTheMetaLineMeetTheirContrast() {
        // Large text needs 3:1 over a white frame (alpha 0.42); small text needs 4.5:1 (alpha 0.55).
        assertTrue(ShelfScrim.rampAlpha(0.53f) >= 0.42f - 1e-4f);
        assertTrue(ShelfScrim.rampAlpha(0.60f) >= 0.55f);
    }

    @Test public void theBottomIsTheDarkest() {
        assertEquals(0.80f, ShelfScrim.rampAlpha(1f), 1e-6f);
        assertEquals(0.80f, ShelfScrim.rampAlpha(2f), 1e-6f);
    }

    @Test public void theStopsAreWhereTheDesignPutsThem() {
        assertEquals(0.40f, ShelfScrim.rampAlpha(0.52f), 1e-6f);
        assertEquals(0.56f, ShelfScrim.rampAlpha(0.60f), 1e-6f);
        assertEquals(0.64f, ShelfScrim.rampAlpha(0.70f), 1e-6f);
    }

    @Test public void thePercentScalesLinearly() {
        assertEquals(0.40f, ShelfScrim.rampAlpha(0.52f, 100), 1e-6f);
        assertEquals(0.20f, ShelfScrim.rampAlpha(0.52f, 50), 1e-6f);
        assertEquals(0f, ShelfScrim.rampAlpha(0.52f, 0), 0f);
        assertEquals(0.40f, ShelfScrim.rampAlpha(0.52f, 250), 1e-6f);   // clamped
        assertEquals(0f, ShelfScrim.rampAlpha(0.52f, -5), 0f);
        assertEquals(0.25f, ShelfScrim.cornerAlpha(0f, 50), 1e-6f);
    }

    @Test public void theCornerFallsFromHalfToNothing() {
        assertEquals(0.50f, ShelfScrim.cornerAlpha(0f), 1e-6f);
        assertEquals(0.28f, ShelfScrim.cornerAlpha(0.5f), 1e-6f);
        assertEquals(0f, ShelfScrim.cornerAlpha(1f), 1e-6f);
        assertEquals(0f, ShelfScrim.cornerAlpha(3f), 0f);
        assertEquals(0.50f, ShelfScrim.cornerAlpha(-1f), 1e-6f);
        float last = 1f;
        for (int i = 0; i <= 100; i++) {
            float a = ShelfScrim.cornerAlpha(i / 100f);
            assertTrue(a <= last + 1e-6f);
            last = a;
        }
    }
}
