package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
