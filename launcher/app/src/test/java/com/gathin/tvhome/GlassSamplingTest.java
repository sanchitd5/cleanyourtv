package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlassSamplingTest {
    @Test public void aFrameOfZerosOrNearZerosIsBlack() {
        assertTrue(GlassSampling.isBlack(new int[]{0, 0xFF000000, 0xFF020202}, 3));
        assertTrue(GlassSampling.isBlack(new int[0], 0));
    }

    @Test public void oneLitPixelIsNotBlack() {
        assertFalse(GlassSampling.isBlack(new int[]{0, 0, 0xFF000300}, 3));
        assertFalse(GlassSampling.isBlack(new int[]{0xFF050000, 0}, 2));
    }

    @Test public void onlyTheCountedPixelsAreLooked_at() {
        assertTrue(GlassSampling.isBlack(new int[]{0, 0xFFFFFFFF}, 1));
    }

    @Test public void theFirstSampleIsAlwaysDue() {
        assertTrue(GlassSampling.due(0, -1, 1000));
    }

    @Test public void samplesAreAtLeastASecondApart() {
        assertFalse(GlassSampling.due(1999, 1000, GlassSampling.MIN_GAP_MS));
        assertTrue(GlassSampling.due(2000, 1000, GlassSampling.MIN_GAP_MS));
    }

    @Test public void liveIsGivenUpOnAfterRepeatedFailures() {
        assertFalse(GlassSampling.giveUp(0));
        assertFalse(GlassSampling.giveUp(GlassSampling.GIVE_UP_AFTER - 1));
        assertTrue(GlassSampling.giveUp(GlassSampling.GIVE_UP_AFTER));
    }

    @Test public void liveWinsOnlyWhenWantedUsableAndPresent() {
        assertEquals(GlassSampling.LIVE, GlassSampling.select(true, true, true, true));
        assertEquals(GlassSampling.LIVE, GlassSampling.select(true, true, true, false));
        assertEquals(GlassSampling.STILL, GlassSampling.select(false, true, true, true));
        assertEquals(GlassSampling.STILL, GlassSampling.select(true, false, true, true));
        assertEquals(GlassSampling.STILL, GlassSampling.select(true, true, false, true));
        assertEquals(GlassSampling.NONE, GlassSampling.select(true, true, false, false));
        assertEquals(GlassSampling.NONE, GlassSampling.select(false, true, true, false));
    }

    @Test public void centerCropKeepsTheScreensShape() {
        int[] out = new int[4];
        GlassSampling.centerCrop(1920, 1080, 1920, 1080, out);
        assertArrayEquals(new int[]{0, 0, 1920, 1080}, out);
        GlassSampling.centerCrop(2400, 1080, 1920, 1080, out);   // wider picture: cut the sides
        assertArrayEquals(new int[]{240, 0, 2160, 1080}, out);
        GlassSampling.centerCrop(1920, 1440, 1920, 1080, out);   // taller picture: cut top and bottom
        assertArrayEquals(new int[]{0, 180, 1920, 1260}, out);
    }

    @Test public void centerCropOfNothingIsTheWholePicture() {
        int[] out = new int[4];
        GlassSampling.centerCrop(100, 50, 0, 0, out);
        assertArrayEquals(new int[]{0, 0, 100, 50}, out);
    }
}
