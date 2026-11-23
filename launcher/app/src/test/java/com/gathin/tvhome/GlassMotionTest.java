package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GlassMotionTest {
    @Test public void stiffnessFallsWithTheSquareOfTheAnimatorScale() {
        assertEquals(600f, GlassMotion.stiffness(600f, 1f), 1e-3);
        assertEquals(150f, GlassMotion.stiffness(600f, 2f), 1e-3);
        assertEquals(2400f, GlassMotion.stiffness(600f, 0.5f), 1e-3);
    }

    @Test public void theScaleIsHeldToHalfAndThree() {
        assertEquals(2400f, GlassMotion.stiffness(600f, 0.1f), 1e-3);
        assertEquals(600f / 9f, GlassMotion.stiffness(600f, 10f), 1e-3);
    }

    @Test public void anUnreadableScaleCountsAsOne() {
        assertEquals(600f, GlassMotion.stiffness(600f, 0f), 1e-3);
        assertEquals(600f, GlassMotion.stiffness(600f, -2f), 1e-3);
        assertEquals(600f, GlassMotion.stiffness(600f, Float.NaN), 1e-3);
    }

    @Test public void biasIsMinusOneAtTheTopZeroAtTheCentreOneAtTheBottom() {
        assertEquals(-1f, GlassMotion.bias(0f, 400f), 1e-6);
        assertEquals(0f, GlassMotion.bias(200f, 400f), 1e-6);
        assertEquals(1f, GlassMotion.bias(400f, 400f), 1e-6);
        assertEquals(-0.5f, GlassMotion.bias(100f, 400f), 1e-6);
    }

    @Test public void biasIsClampedAndSafeForAnEmptyPanel() {
        assertEquals(-1f, GlassMotion.bias(-50f, 400f), 1e-6);
        assertEquals(1f, GlassMotion.bias(900f, 400f), 1e-6);
        assertEquals(0f, GlassMotion.bias(10f, 0f), 1e-6);
    }

    @Test public void stiffnessAtScalesZeroHalfOneAndTwoForEverySpring() {
        float[] bases = {GlassMotion.OPEN_STIFFNESS, GlassMotion.SHEET_STIFFNESS, GlassMotion.PARALLAX_STIFFNESS};
        for (float base : bases) {
            assertEquals(base, GlassMotion.stiffness(base, 0f), 1e-3);       // 0 is the "unknown" case; the caller snaps
            assertEquals(base * 4f, GlassMotion.stiffness(base, 0.5f), 1e-3);
            assertEquals(base, GlassMotion.stiffness(base, 1f), 1e-3);
            assertEquals(base / 4f, GlassMotion.stiffness(base, 2f), 1e-3);
        }
    }

    @Test public void animatorsOffOrAnimationsOffSnap() {
        assertEquals(GlassMotion.STYLE_SNAP, GlassMotion.style(false, true, false));
        assertEquals(GlassMotion.STYLE_SNAP, GlassMotion.style(true, false, false));
        assertEquals(GlassMotion.STYLE_SNAP, GlassMotion.style(false, false, true));
    }

    @Test public void reducedMotionIsAlphaOnlyAndTheRestIsFull() {
        assertEquals(GlassMotion.STYLE_ALPHA, GlassMotion.style(true, true, true));
        assertEquals(GlassMotion.STYLE_FULL, GlassMotion.style(true, true, false));
    }

    @Test public void offsetMapsAFocusedRowToTheBandShiftAndRimTurn() {
        // a 400 px panel: a row centred at 100 px is bias -0.5, so the band moves 2.5 dp up and the rim turns 5 degrees back
        float bias = GlassMotion.bias(100f, 400f);
        assertEquals(-0.5f, bias, 1e-6);
        assertEquals(-2.5f * 2f, GlassSurface.bandShiftPx(bias, 2f), 1e-4);
        assertEquals(-5f, GlassSurface.rimTurnDegrees(bias), 1e-4);
        assertEquals(5f * 2f, GlassSurface.bandShiftPx(GlassMotion.bias(400f, 400f), 2f), 1e-4);
        assertEquals(10f, GlassSurface.rimTurnDegrees(GlassMotion.bias(400f, 400f)), 1e-4);
    }

    @Test public void crossfadeTimingIsLinearAndHeldToTheDuration() {
        assertEquals(200, GlassMotion.fadeMs(false));   // the first live sample replacing the still
        assertEquals(300, GlassMotion.fadeMs(true));    // a resample
        assertEquals(0f, GlassMotion.fadeFraction(0, 200), 1e-6);
        assertEquals(0.5f, GlassMotion.fadeFraction(100, 200), 1e-6);
        assertEquals(0.5f, GlassMotion.fadeFraction(150, 300), 1e-6);
        assertEquals(1f, GlassMotion.fadeFraction(900, 300), 1e-6);
        assertEquals(1f, GlassMotion.fadeFraction(10, 0), 1e-6);
        assertEquals(0f, GlassMotion.fadeFraction(-5, 200), 1e-6);
    }

    @Test public void crossfadeAlphaRunsFromTransparentToOpaqueAndIsMonotonic() {
        assertEquals(0, GlassMotion.fadeAlpha255(0f));
        assertEquals(255, GlassMotion.fadeAlpha255(1f));
        assertEquals(255, GlassMotion.fadeAlpha255(Float.NaN));
        assertEquals(0, GlassMotion.fadeAlpha255(-1f));
        assertEquals(255, GlassMotion.fadeAlpha255(2f));
        int last = -1;
        for (int i = 0; i <= 20; i++) {
            int a = GlassMotion.fadeAlpha255(i / 20f);
            assertTrue(a >= last);
            last = a;
        }
    }

    @Test public void rowScaleFollowsTheFocusAndPressSpec() {
        assertEquals(1f, GlassMotion.rowScale(false, false, false), 1e-6);
        assertEquals(1.02f, GlassMotion.rowScale(true, false, false), 1e-6);
        assertEquals(1.04f, GlassMotion.rowScale(true, true, false), 1e-6);
        assertEquals(0.985f, GlassMotion.rowScale(true, false, true), 1e-6);
        assertEquals(1f, GlassMotion.rowScale(false, true, true), 1e-6);
    }
}
