package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AccentTest {
    private static int r(int c) { return (c >> 16) & 0xFF; }
    private static int g(int c) { return (c >> 8) & 0xFF; }
    private static int b(int c) { return c & 0xFF; }
    private static float v(int c) { return Math.max(r(c), Math.max(g(c), b(c))) / 255f; }
    private static float s(int c) {
        float max = Math.max(r(c), Math.max(g(c), b(c))), min = Math.min(r(c), Math.min(g(c), b(c)));
        return max == 0 ? 0 : (max - min) / max;
    }

    @Test public void aDarkSaturatedColourIsLifted() {
        int out = Accent.legible(0xFF1A0A3C);   // deep indigo
        assertTrue(v(out) >= 0.75f - 0.005f);
        assertTrue(b(out) > r(out) && b(out) > g(out));   // still the same hue family
    }

    @Test public void anOverSaturatedColourIsCapped() {
        int out = Accent.legible(0xFFFF0000);
        assertTrue(s(out) <= 0.85f + 0.01f);
        assertTrue(r(out) > g(out));
    }

    @Test public void aColourAlreadyFineIsKept() {
        int in = 0xFFD94040;   // V 0.85, S 0.70
        int out = Accent.legible(in);
        assertEquals(r(in), r(out), 1);
        assertEquals(g(in), g(out), 1);
        assertEquals(b(in), b(out), 1);
    }

    @Test public void greysStayGrey() {
        for (int c : new int[]{0xFF000000, 0xFF808080, 0xFFFFFFFF, 0xFF202020}) {
            int out = Accent.legible(c);
            assertEquals(r(out), g(out));
            assertEquals(g(out), b(out));
            assertTrue(r(out) >= 191);
        }
        assertEquals(0xFFFFFFFF, Accent.legible(0xFFFFFFFF));
    }

    @Test public void alphaIsAlwaysOpaque() {
        assertEquals(0xFF, Accent.legible(0x00123456) >>> 24);
        assertEquals(0xFF, Accent.legible(0x80FF8800) >>> 24);
    }

    @Test public void everyHueSectorComesOutAsAColour() {
        int[] in = {0xFF400000, 0xFF404000, 0xFF004000, 0xFF004040, 0xFF000040, 0xFF400040};
        for (int c : in) {
            int out = Accent.legible(c);
            assertTrue(v(out) >= 0.74f);
            assertTrue(s(out) > 0.5f && s(out) <= 0.86f);
        }
    }
}
