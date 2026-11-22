package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** clock_aerial: where a leg of the screensaver picture's drift ends (ClockScene.pan). */
public class ClockPanTest {
    private static final float[] STEPS = {0f, 0.25f, 0.5f, 0.75f, 0.999f};
    /** Rounding only: a thousandth of the picture is two pixels at 1080p, under the zoom's own 77 at its least. */
    private static final float SLACK = 1e-5f;

    /** The part of the picture's size the zoom leaves over on each side. */
    private static float room(float scale) {
        return (scale - 1f) / 2f;
    }

    @Test public void thePictureIsAlwaysZoomedIn() {
        for (float zoom : STEPS) {
            float scale = ClockScene.pan(zoom, 0.5f, 0.5f)[0];
            assertTrue("scale " + scale, scale >= 1.08f && scale <= 1.16f);
        }
    }

    @Test public void theMiddleOfTheRangeLeavesThePictureCentred() {
        float[] to = ClockScene.pan(0.5f, 0.5f, 0.5f);
        assertEquals(0f, to[1], SLACK);
        assertEquals(0f, to[2], SLACK);
    }

    @Test public void theEndsOfTheRangeShiftOppositeWays() {
        assertTrue(ClockScene.pan(0.5f, 0f, 0f)[1] < 0f);
        assertTrue(ClockScene.pan(0.5f, 0.999f, 0f)[1] > 0f);
        assertTrue(ClockScene.pan(0.5f, 0f, 0f)[2] < 0f);
        assertTrue(ClockScene.pan(0.5f, 0f, 0.999f)[2] > 0f);
    }

    @Test public void noEndShowsThePicturesEdge() {
        for (float zoom : STEPS) {
            for (float x : STEPS) {
                for (float y : STEPS) {
                    float[] to = ClockScene.pan(zoom, x, y);
                    assertTrue(Math.abs(to[1]) <= room(to[0]) + SLACK);
                    assertTrue(Math.abs(to[2]) <= room(to[0]) + SLACK);
                }
            }
        }
    }

    @Test public void neitherDoesTheWayFromOneEndToTheNext() {
        // The animation moves scale and shift together, so every point of a leg lies between its two ends;
        // the first leg starts from the picture at rest: not zoomed, not shifted.
        float[][] ends = {{1f, 0f, 0f}, ClockScene.pan(0f, 0f, 0.999f), ClockScene.pan(0.999f, 0.999f, 0f),
                ClockScene.pan(0f, 0.999f, 0.999f), ClockScene.pan(0.999f, 0f, 0f)};
        for (float[] from : ends) {
            for (float[] to : ends) {
                for (float t : STEPS) {
                    float scale = from[0] + (to[0] - from[0]) * t;
                    assertTrue(Math.abs(from[1] + (to[1] - from[1]) * t) <= room(scale) + SLACK);
                    assertTrue(Math.abs(from[2] + (to[2] - from[2]) * t) <= room(scale) + SLACK);
                }
            }
        }
    }
}
