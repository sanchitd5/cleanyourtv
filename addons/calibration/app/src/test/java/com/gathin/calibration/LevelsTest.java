package com.gathin.calibration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LevelsTest {
    @Test public void blackAndWhiteByRange() {
        assertEquals(16, Levels.black(true));
        assertEquals(235, Levels.white(true));
        assertEquals(0, Levels.black(false));
        assertEquals(255, Levels.white(false));
    }

    @Test public void greyPercent() {
        assertEquals(16, Levels.grey(0, true));
        assertEquals(235, Levels.grey(100, true));
        assertEquals(126, Levels.grey(50, true));   // 16 + 109.5 rounds up
        assertEquals(27, Levels.grey(5, true));
        assertEquals(0, Levels.grey(0, false));
        assertEquals(255, Levels.grey(100, false));
        assertEquals(128, Levels.grey(50, false));
        assertEquals(13, Levels.grey(5, false));
        assertEquals(255, Levels.grey(400, false));   // clamped
        assertEquals(0, Levels.grey(-3, false));
    }

    @Test public void plugeBarsAreNearBlack() {
        assertEquals(0, Levels.PLUGE_LIMITED[0]);
        assertEquals(25, Levels.PLUGE_LIMITED[Levels.PLUGE_LIMITED.length - 1]);
        assertTrue(contains(Levels.PLUGE_LIMITED, 16));   // the bar equal to the background
        for (int i = 1; i < Levels.PLUGE_LIMITED.length; i++) {
            assertTrue(Levels.PLUGE_LIMITED[i] > Levels.PLUGE_LIMITED[i - 1]);
        }
        for (int i = 1; i < Levels.PLUGE_FULL.length; i++) {
            assertTrue(Levels.PLUGE_FULL[i] > Levels.PLUGE_FULL[i - 1]);
        }
        assertEquals(0, Levels.PLUGE_FULL[0]);
    }

    @Test public void whiteClipCodes() {
        int[] codes = Levels.whiteClipCodes();
        assertEquals(21, codes.length);
        assertEquals(235, codes[0]);
        assertEquals(255, codes[20]);
    }

    @Test public void gammaPatchIsHalfLight() {
        assertEquals(186, Levels.gammaPatch(2.2, false));
        assertEquals(16 + Math.round(219 * Math.pow(0.5, 1 / 2.4)), Levels.gammaPatch(2.4, true));
        assertTrue(Levels.gammaPatch(1.8, false) < Levels.gammaPatch(2.6, false));
    }

    @Test public void colourBars() {
        assertArrayEquals(new int[]{180, 180, 180}, Levels.bar(Levels.BAR_MASKS[0], 0.75, true));
        assertArrayEquals(new int[]{255, 255, 0}, Levels.bar(Levels.R | Levels.G, 1, false));
        assertArrayEquals(new int[]{16, 16, 180}, Levels.bar(Levels.B, 0.75, true));
        assertArrayEquals(new int[]{191, 191, 191}, Levels.bar(Levels.BAR_MASKS[0], 0.75, false));
        assertEquals(7, Levels.BAR_MASKS.length);
    }

    @Test public void saturationSweepGoesFromGreyToPrimary() {
        assertArrayEquals(new int[]{128, 128, 128}, Levels.saturation(Levels.R, 0, 6, false));
        assertArrayEquals(new int[]{255, 0, 0}, Levels.saturation(Levels.R, 5, 6, false));
        assertArrayEquals(new int[]{16, 235, 235}, Levels.saturation(Levels.G | Levels.B, 5, 6, true));
        int[] mid = Levels.saturation(Levels.R, 2, 6, false);
        assertTrue(mid[0] > 128 && mid[1] < 128);
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }
}
