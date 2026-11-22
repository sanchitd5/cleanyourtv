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
        assertEquals(0.82f, ShelfScrim.rampAlpha(1f), 1e-6f);
        assertEquals(0.82f, ShelfScrim.rampAlpha(2f), 1e-6f);
    }

    @Test public void theStopsAreWhereTheDesignPutsThem() {
        assertEquals(0.44f, ShelfScrim.rampAlpha(0.52f), 1e-6f);
        assertEquals(0.62f, ShelfScrim.rampAlpha(0.60f), 1e-6f);
        assertEquals(0.68f, ShelfScrim.rampAlpha(0.70f), 1e-6f);
    }

    @Test public void thePercentScalesLinearly() {
        assertEquals(0.44f, ShelfScrim.rampAlpha(0.52f, 100), 1e-6f);
        assertEquals(0.22f, ShelfScrim.rampAlpha(0.52f, 50), 1e-6f);
        assertEquals(0f, ShelfScrim.rampAlpha(0.52f, 0), 0f);
        assertEquals(0.44f, ShelfScrim.rampAlpha(0.52f, 250), 1e-6f);   // clamped
        assertEquals(0f, ShelfScrim.rampAlpha(0.52f, -5), 0f);
        assertEquals(0.33f, ShelfScrim.cornerAlpha(0f, 50), 1e-6f);
    }

    /** Contrast of white text at textAlpha over a pure white frame darkened by a black scrim of alpha a. */
    private static double contrast(double a, double textAlpha) {
        double bg = 1 - a, text = textAlpha + (1 - textAlpha) * bg;
        double l1 = lum(text), l2 = lum(bg);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    private static double lum(double c) { return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

    @Test public void theDateAndTheClockStayReadableOverAWhiteFrame() {
        // The date (90 % ink) sits inside the corner's plateau, out to 0.6 of its radius.
        for (float r = 0f; r <= 0.6f; r += 0.02f) {
            assertTrue("r=" + r, contrast(ShelfScrim.cornerAlpha(r), 0.90) >= 4.5);
        }
    }

    @Test public void theMetaLineAndTheCaptionStayReadableOverAWhiteFrame() {
        // The meta line and the docked caption sit between 0.58 and 0.70 of the height.
        for (float y = 0.58f; y <= 1f; y += 0.02f) {
            assertTrue("y=" + y, contrast(ShelfScrim.rampAlpha(y), 0.90) >= 4.5);
        }
        assertTrue(contrast(ShelfScrim.rampAlpha(0.53f), 1.0) >= 3.0);   // the title is large text
    }

    @Test public void theCornerFallsToNothing() {
        assertEquals(0.66f, ShelfScrim.cornerAlpha(0f), 1e-6f);
        assertEquals(0.62f, ShelfScrim.cornerAlpha(0.6f), 1e-6f);
        assertEquals(0f, ShelfScrim.cornerAlpha(1f), 1e-6f);
        assertEquals(0f, ShelfScrim.cornerAlpha(3f), 0f);
        assertEquals(0.66f, ShelfScrim.cornerAlpha(-1f), 1e-6f);
        float last = 1f;
        for (int i = 0; i <= 100; i++) {
            float a = ShelfScrim.cornerAlpha(i / 100f);
            assertTrue(a <= last + 1e-6f);
            last = a;
        }
    }
}
