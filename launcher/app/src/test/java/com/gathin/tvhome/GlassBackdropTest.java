package com.gathin.tvhome;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GlassBackdropTest {
    @After public void reset() {
        GlassBackdrop.clear();
        GlassBackdrop.setHomeVisible(false);
    }

    @Test public void startsEmpty() {
        GlassBackdrop.clear();
        assertNull(GlassBackdrop.still());
        assertNull(GlassBackdrop.live());
        assertNull(GlassBackdrop.current());
        assertTrue(GlassBackdrop.liveAvailable());
        assertFalse(GlassBackdrop.requestLive(null, new float[]{1f, 1f}));
    }

    @Test public void homeVisibilityIsRemembered() {
        GlassBackdrop.setHomeVisible(true);
        assertTrue(GlassBackdrop.homeVisible());
        GlassBackdrop.setHomeVisible(false);
        assertFalse(GlassBackdrop.homeVisible());
    }

    @Test public void generationMovesAndListenersAreTold() {
        final int[] told = {0};
        GlassBackdrop.Listener l = () -> told[0]++;
        GlassBackdrop.addListener(l);
        GlassBackdrop.addListener(l);          // twice is once
        int g = GlassBackdrop.generation();
        GlassBackdrop.setHomeVisible(true);
        assertEquals(1, told[0]);
        assertTrue(GlassBackdrop.generation() != g);
        GlassBackdrop.removeListener(l);
        GlassBackdrop.setHomeVisible(false);
        assertEquals(1, told[0]);
    }

    @Test public void failingToReadTheClipIsRemembered() {
        GlassBackdrop.markLiveUnavailable();
        assertFalse(GlassBackdrop.liveAvailable());
        GlassBackdrop.clear();
        assertTrue(GlassBackdrop.liveAvailable());
    }

    @Test public void thePanelCentreStaysPutUnderTheMagnification() {
        float[] m = new float[4];
        // A 400x300 panel at (1000, 100) on 1920x1080. The backdrop pixel under the panel's centre must still land there.
        GlassBackdrop.align(1000f, 100f, 400f, 300f, 1920f, 1080f, GlassBackdrop.MAGNIFY, m);
        float bx = (1000f + 200f) / 15f, by = (100f + 150f) / 15f;     // the bitmap pixel at the centre, screen / 15
        assertEquals(200f, bx * m[0] + m[2], 1e-2);
        assertEquals(150f, by * m[1] + m[3], 1e-2);
        assertEquals(15f * GlassBackdrop.MAGNIFY, m[0], 1e-4);
    }

    @Test public void withoutMagnificationTheBackdropIsScreenAligned() {
        float[] m = new float[4];
        GlassBackdrop.align(300f, 200f, 400f, 300f, 1920f, 1080f, 1f, m);
        assertEquals(15f, m[0], 1e-4);
        assertEquals(15f, m[1], 1e-4);
        assertEquals(-300f, m[2], 1e-3);
        assertEquals(-200f, m[3], 1e-3);
    }

    @Test public void theRegionUnderAPanelIsInBitmapPixelsAndKeptInside() {
        int[] r = new int[4];
        GlassBackdrop.regionUnder(1000f, 100f, 400f, 300f, 1920f, 1080f, r);
        assertEquals(66, r[0]);
        assertEquals(6, r[1]);
        assertEquals(94, r[2]);
        assertEquals(27, r[3]);
        GlassBackdrop.regionUnder(-50f, -50f, 5000f, 5000f, 1920f, 1080f, r);
        assertEquals(0, r[0]);
        assertEquals(0, r[1]);
        assertEquals(GlassBackdrop.WIDTH, r[2]);
        assertEquals(GlassBackdrop.HEIGHT, r[3]);
    }
}
