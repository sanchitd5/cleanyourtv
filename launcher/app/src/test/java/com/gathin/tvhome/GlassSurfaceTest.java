package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The geometry and material selection behind GlassPanelDrawable (HOME-DESIGN.md 19.3 and 19.5). */
public class GlassSurfaceTest {
    private static final float EPS = 0.001f;

    @Test
    public void concentricRadii() {
        assertEquals(21f, GlassSurface.concentric(GlassSurface.POPUP_RADIUS_DP, GlassSurface.POPUP_PAD_DP), EPS);
        assertEquals(GlassSurface.ROW_RADIUS_DP, GlassSurface.concentric(27f, 6f), EPS);
        assertEquals(18f, GlassSurface.concentric(GlassSurface.SETTINGS_RADIUS_DP, 6f), EPS);
        assertEquals(0f, GlassSurface.concentric(4f, 9f), EPS);
    }

    @Test
    public void radiusFitsSmallPanels() {
        assertEquals(21f, GlassSurface.fitRadius(27f, 42f, 100f), EPS);
        assertEquals(27f, GlassSurface.fitRadius(27f, 300f, 300f), EPS);
        assertEquals(0f, GlassSurface.fitRadius(27f, 0f, 10f), EPS);
    }

    @Test
    public void rimRunsTopLeftToBottomRight() {
        float[] l = new float[4];
        GlassSurface.gradientLine(200f, 200f, GlassSurface.RIM_ANGLE, l);
        assertEquals(0f, l[0], 0.01f);
        assertEquals(0f, l[1], 0.01f);
        assertEquals(200f, l[2], 0.01f);
        assertEquals(200f, l[3], 0.01f);
    }

    @Test
    public void gradientLineStraightDownAndAcross() {
        float[] l = new float[4];
        GlassSurface.gradientLine(100f, 40f, 180f, l);
        assertEquals(50f, l[0], 0.01f);
        assertEquals(0f, l[1], 0.01f);
        assertEquals(50f, l[2], 0.01f);
        assertEquals(40f, l[3], 0.01f);
        GlassSurface.gradientLine(100f, 40f, 90f, l);
        assertEquals(0f, l[0], 0.01f);
        assertEquals(100f, l[2], 0.01f);
        assertEquals(20f, l[1], 0.01f);
    }

    @Test
    public void bandsFollowTheSpec() {
        assertEquals(28f, GlassSurface.bandTopEnd(100f), EPS);
        assertEquals(88f, GlassSurface.bandBottomStart(100f), EPS);
    }

    @Test
    public void materialsPickTheirNumbers() {
        assertEquals(GlassTint.MODE_GLASS, GlassTint.mode(true, false, true));
        assertEquals(GlassTint.MODE_TINTED, GlassTint.mode(true, false, false));
        assertEquals(GlassTint.MODE_SOLID, GlassTint.mode(true, true, true));
        assertEquals(GlassTint.MODE_SOLID, GlassTint.mode(false, false, true));

        assertEquals(0.40f, GlassSurface.tintAlpha(GlassTint.MODE_GLASS, 0f), EPS);
        assertEquals(0.72f, GlassSurface.tintAlpha(GlassTint.MODE_GLASS, 1f), EPS);
        assertEquals(0.78f, GlassSurface.tintAlpha(GlassTint.MODE_TINTED, 0.3f), EPS);
        assertEquals(1f, GlassSurface.tintAlpha(GlassTint.MODE_SOLID, 0.3f), EPS);

        assertEquals(0x0E1016, GlassSurface.tintColor(GlassTint.MODE_TINTED) & 0xFFFFFF);
        assertEquals(0x16181D, GlassSurface.tintColor(GlassTint.MODE_SOLID) & 0xFFFFFF);
        assertEquals(0x0B0D12, GlassSurface.tintColor(GlassTint.MODE_GLASS) & 0xFFFFFF);

        assertEquals(0.14f, GlassSurface.bandAlpha(GlassTint.MODE_GLASS, 0f), EPS);
        assertEquals(0f, GlassSurface.bandAlpha(GlassTint.MODE_GLASS, 1f), EPS);
        assertEquals(0.08f, GlassSurface.bandAlpha(GlassTint.MODE_TINTED, 0.9f), EPS);
        assertEquals(0.05f, GlassSurface.bandAlpha(GlassTint.MODE_SOLID, 0.9f), EPS);
        assertEquals(0.05f / 3f, GlassSurface.bottomBandAlpha(GlassTint.MODE_SOLID, 0f), EPS);
    }

    @Test
    public void onlyGlassDrawsTheBackdrop() {
        assertTrue(GlassSurface.drawsBackdrop(GlassTint.MODE_GLASS, true, true));
        assertFalse(GlassSurface.drawsBackdrop(GlassTint.MODE_GLASS, false, true));
        assertFalse(GlassSurface.drawsBackdrop(GlassTint.MODE_GLASS, true, false));
        assertFalse(GlassSurface.drawsBackdrop(GlassTint.MODE_TINTED, true, true));
        assertFalse(GlassSurface.drawsBackdrop(GlassTint.MODE_SOLID, true, true));
        assertTrue(GlassSurface.flatRim(GlassTint.MODE_SOLID));
        assertFalse(GlassSurface.flatRim(GlassTint.MODE_GLASS));
    }

    @Test
    public void alphaHelpers() {
        assertEquals(0, GlassSurface.alpha255(-1f));
        assertEquals(255, GlassSurface.alpha255(2f));
        assertEquals(0x40, GlassSurface.alpha255(0.25f));
        assertEquals(0xB8161718 & 0xFF000000 | 0x0B0D12, GlassSurface.withAlpha(0x0B0D12, 0.72f));
    }

    @Test
    public void biasMapsToShiftAndTurn() {
        assertEquals(10f, GlassSurface.rimTurnDegrees(1f), EPS);
        assertEquals(-10f, GlassSurface.rimTurnDegrees(-5f), EPS);
        assertEquals(10f, GlassSurface.bandShiftPx(1f, 2f), EPS);
        assertEquals(0f, GlassSurface.bandShiftPx(Float.NaN, 2f), EPS);
        assertEquals(0f, GlassSurface.rimTurnDegrees(0f), EPS);
    }
}
