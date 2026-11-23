package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GlassTintTest {
    private static int gray(int v) {
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** The sRGB grey whose linear luminance is {@code l}. */
    private static int greyOfLuma(double l) {
        return gray((int) Math.round(255 * Math.pow(l, 1 / 2.2)));
    }

    /** The panel colour at the top edge, where the lens band is strongest, over a uniform backdrop of luminance l. */
    private static int glassPanelTop(double l) {
        float peak = (float) l;
        int tinted = GlassTint.over(GlassTint.GLASS_TINT, GlassTint.tintAlpha(peak), greyOfLuma(l));
        return GlassTint.over(0xFFFFFFFF, GlassTint.bandAlpha(peak), tinted);
    }

    private static void assertReadable(int panel) {
        assertTrue(GlassTint.contrast(GlassTint.PRIMARY_TEXT, panel) >= 4.5);
        int secondary = GlassTint.over(0xFFFFFFFF, (GlassTint.SECONDARY_TEXT >>> 24) / 255f, panel);
        assertTrue("secondary " + GlassTint.contrast(secondary, panel), GlassTint.contrast(secondary, panel) >= 4.5);
    }

    @Test public void lumaIsLinearLightWithTheStatedWeights() {
        assertEquals(0f, GlassTint.luma(0xFF000000), 1e-6);
        assertEquals(1f, GlassTint.luma(0xFFFFFFFF), 1e-4);
        assertEquals(0.2126f, GlassTint.luma(0xFFFF0000), 1e-4);
        assertEquals(0.7152f, GlassTint.luma(0xFF00FF00), 1e-4);
        assertEquals(0.0722f, GlassTint.luma(0xFF0000FF), 1e-4);
    }

    @Test public void peakPicksTheBrightCellOfAHalfWhiteHalfBlackField() {
        int w = 128, h = 72;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = x < w / 2 ? 0xFFFFFFFF : 0xFF000000;
        assertEquals(1f, GlassTint.peak(px, w, h, 0, 0, w, h), 1e-3);
        assertEquals(0f, GlassTint.peak(px, w, h, w / 2, 0, w, h), 1e-6);
        assertEquals(1f, GlassTint.peak(px, w, h, w / 4, 10, w * 3 / 4, 60), 1e-3);
    }

    @Test public void peakIsTheBrightestCellNotTheAverage() {
        int w = 128, h = 72;
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFF000000);
        for (int y = 0; y < 18; y++) for (int x = 0; x < 32; x++) px[y * w + x] = 0xFFFFFFFF;   // one grid cell
        assertEquals(1f, GlassTint.peak(px, w, h, 0, 0, w, h), 1e-3);
    }

    @Test public void peakOfNothingIsTheWorstCase() {
        int[] px = new int[128 * 72];
        assertEquals(1f, GlassTint.peak(px, 128, 72, 50, 50, 50, 60), 0f);
        assertEquals(1f, GlassTint.peak(px, 128, 72, 200, 0, 300, 10), 0f);
        assertEquals(1f, GlassTint.peak(null, 128, 72, 0, 0, 10, 10), 0f);
    }

    @Test public void peakClampsARegionHangingOverTheEdge() {
        int w = 128, h = 72;
        int[] px = new int[w * h];
        Arrays.fill(px, 0xFF000000);
        assertEquals(0f, GlassTint.peak(px, w, h, -40, -40, 20, 20), 1e-6);
    }

    @Test public void tintAlphaIsFortyAtBlackSeventyTwoAtWhiteMonotonicAndClamped() {
        assertEquals(0.40f, GlassTint.tintAlpha(0f), 1e-6);
        assertEquals(0.72f, GlassTint.tintAlpha(1f), 1e-6);
        assertEquals(0.40f, GlassTint.tintAlpha(-3f), 1e-6);
        assertEquals(0.72f, GlassTint.tintAlpha(5f), 1e-6);
        float last = 0f;
        for (int i = 0; i <= 100; i++) {
            float a = GlassTint.tintAlpha(i / 100f);
            assertTrue(a >= last);
            last = a;
        }
    }

    @Test public void bandAlphaFallsWithBrightness() {
        assertEquals(0.14f, GlassTint.bandAlpha(0f), 1e-6);
        assertEquals(0f, GlassTint.bandAlpha(1f), 1e-6);
        assertEquals(0.07f, GlassTint.bandAlpha(0.5f), 1e-6);
    }

    @Test public void glassKeepsBothTextColoursAtFourPointFiveForEveryBackdrop() {
        for (int i = 0; i <= 20; i++) assertReadable(glassPanelTop(i * 0.05));
    }

    @Test public void theFocusedCapsuleKeepsItsInkReadableOnEveryBackdrop() {
        // The capsule runs from #F2FFFFFF to #E0FFFFFF over the panel: over 15:1 at the top, over 13:1 at the bottom.
        for (int i = 0; i <= 20; i++) {
            int panel = glassPanelTop(i * 0.05);
            assertTrue(GlassTint.contrast(GlassTint.FOCUS_INK, GlassTint.over(0xFFFFFFFF, 0xF2 / 255f, panel)) >= 15);
            assertTrue(GlassTint.contrast(GlassTint.FOCUS_INK, GlassTint.over(0xFFFFFFFF, 0xE0 / 255f, panel)) >= 13);
        }
    }

    @Test public void tintedOverAPureWhiteAppFrameMatchesTheTable() {
        int panel = GlassTint.over(0xFFFFFFFF, GlassTint.TINTED_BAND, GlassTint.over(GlassTint.TINTED_TINT, GlassTint.TINTED_ALPHA, 0xFFFFFFFF));
        assertEquals(0x52, (panel >> 16) & 0xFF, 6);          // "about #525356"
        assertTrue(GlassTint.contrast(GlassTint.PRIMARY_TEXT, panel) >= 7.0);
        assertReadable(panel);
    }

    @Test public void solidIsReadableAndMatchesTheTable() {
        int panel = GlassTint.over(0xFFFFFFFF, GlassTint.SOLID_BAND, GlassTint.SOLID_TINT);
        assertTrue(GlassTint.contrast(GlassTint.PRIMARY_TEXT, panel) > 11);
        assertReadable(panel);
        assertTrue(GlassTint.contrast(GlassTint.PRIMARY_TEXT, GlassTint.SOLID_TINT) > 15);
    }

    @Test public void selectionRimIsAtLeastThreeToOneAgainstTheLightestPanel() {
        assertTrue(GlassTint.contrast(GlassTint.SELECTED, glassPanelTop(1.0)) >= 3);
        assertTrue(GlassTint.contrast(GlassTint.SELECTED, GlassTint.over(GlassTint.TINTED_TINT, GlassTint.TINTED_ALPHA, 0xFFFFFFFF)) >= 3);
    }

    @Test public void overBlendsTowardsTheForeground() {
        assertEquals(0xFF808080, GlassTint.over(0xFFFFFFFF, 0.5f, 0xFF000000));
        assertEquals(0xFF000000, GlassTint.over(0xFF000000, 1f, 0xFFFFFFFF));
        assertEquals(0xFFFFFFFF, GlassTint.over(0xFF000000, 0f, 0xFFFFFFFF));
        assertEquals(21.0, GlassTint.contrast(0xFF000000, 0xFFFFFFFF), 1e-6);
        assertEquals(1.0, GlassTint.contrast(0xFF123456, 0xFF123456), 1e-9);
    }

    @Test public void modeFollowsTheSettingTheReductionAndWhetherHomeIsBehind() {
        assertEquals(GlassTint.MODE_GLASS, GlassTint.mode(true, false, true));
        assertEquals(GlassTint.MODE_TINTED, GlassTint.mode(true, false, false));
        assertEquals(GlassTint.MODE_SOLID, GlassTint.mode(true, true, true));
        assertEquals(GlassTint.MODE_SOLID, GlassTint.mode(true, true, false));
        assertEquals(GlassTint.MODE_SOLID, GlassTint.mode(false, false, true));
        assertEquals(GlassTint.MODE_SOLID, GlassTint.mode(false, false, false));
    }
}
