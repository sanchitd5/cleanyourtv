package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WallpaperSizeTest {
    @Test public void veryWidePictureIsRefused() {
        assertFalse(WallpaperSize.accepted(65535, 2159));
    }

    @Test public void fourKPictureIsAccepted() {
        assertTrue(WallpaperSize.accepted(3840, 2160));
        assertTrue(WallpaperSize.accepted(1920, 1080));
    }

    @Test public void unreadablePictureIsRefused() {
        assertFalse(WallpaperSize.accepted(-1, -1));
        assertFalse(WallpaperSize.accepted(0, 1080));
    }

    @Test public void aClipOfTheScreensShapeIsNotScaled() {
        assertArrayEquals(new float[]{1f, 1f}, WallpaperSize.cropScale(1920, 1080, 1920, 1080), 0.0001f);
        assertArrayEquals(new float[]{1f, 1f}, WallpaperSize.cropScale(1280, 720, 1920, 1080), 0.0001f);
    }

    @Test public void aWiderClipIsCutAtTheSides() {
        // 4096x2160 on 1920x1080: the height fills, and the width hangs over by 4096/3840.
        float[] scale = WallpaperSize.cropScale(4096, 2160, 1920, 1080);
        assertEquals(4096f / 3840f, scale[0], 0.0001f);
        assertEquals(1f, scale[1], 0.0001f);
    }

    @Test public void aTallerClipIsCutTopAndBottom() {
        float[] scale = WallpaperSize.cropScale(1620, 1080, 1920, 1080);
        assertEquals(1f, scale[0], 0.0001f);
        assertEquals((16f / 9f) / 1.5f, scale[1], 0.0001f);
    }

    @Test public void anUnknownSizeLeavesTheClipAlone() {
        assertArrayEquals(new float[]{1f, 1f}, WallpaperSize.cropScale(0, 0, 1920, 1080), 0.0001f);
        assertArrayEquals(new float[]{1f, 1f}, WallpaperSize.cropScale(1920, 1080, 0, 0), 0.0001f);
    }

    @Test public void fourKOn1080pSamplesByTwo() {
        assertEquals(2, WallpaperSize.sampleSize(3840, 2160, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1920, 1080, 1920, 1080));
    }

    @Test public void photoBetweenSampleSizesIsScaledToTheScreen() {
        // 3000x2000 keeps sample size 1; the cover is limited by the width, so it decodes 1920 wide.
        assertEquals(1, WallpaperSize.sampleSize(3000, 2000, 1920, 1080));
        assertArrayEquals(new int[]{3000, 1920}, WallpaperSize.coverScale(3000, 2000, 1920, 1080));
    }

    @Test public void widePictureIsScaledByItsHeight() {
        assertArrayEquals(new int[]{2000, 1080}, WallpaperSize.coverScale(4000, 2000, 1920, 1080));
    }

    @Test public void smallPictureIsNotScaled() {
        assertEquals(1, WallpaperSize.sampleSize(1280, 720, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1280, 720, 1920, 1080));
    }

    @Test
    public void aPhoneSizedPhotoIsAcceptedOnceSampledDown() {
        int s = WallpaperSize.sampleSize(4032, 3024, 1920, 1080);
        org.junit.Assert.assertTrue(WallpaperSize.accepted(4032 / s, 3024 / s));
        int t = WallpaperSize.sampleSize(65535, 2159, 1920, 1080);
        org.junit.Assert.assertFalse(WallpaperSize.accepted(65535 / t, 2159 / t));
    }

    @Test public void aClipOfTheViewsShapeShowsAllOfItsFrame() {
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f}, WallpaperSize.sourceRect(WallpaperSize.cropScale(1280, 720, 1920, 1080)), 0.0001f);
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f}, WallpaperSize.sourceRect(new float[]{1f, 1f}), 0.0001f);
    }

    @Test public void aWiderClipShowsTheMiddleOfItsWidth() {
        float[] r = WallpaperSize.sourceRect(WallpaperSize.cropScale(2560, 1080, 1920, 1080));   // 21:9 on 16:9
        assertArrayEquals(new float[]{0.125f, 0f, 0.875f, 1f}, r, 0.0001f);
    }

    @Test public void aTallerClipShowsTheMiddleOfItsHeight() {
        float[] r = WallpaperSize.sourceRect(WallpaperSize.cropScale(1920, 1440, 1920, 1080));   // 4:3
        assertArrayEquals(new float[]{0f, 0.125f, 1f, 0.875f}, r, 0.0001f);
    }

    @Test public void anUnknownScaleShowsTheWholeFrame() {
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f}, WallpaperSize.sourceRect(null), 0.0001f);
        assertArrayEquals(new float[]{0f, 0f, 1f, 1f}, WallpaperSize.sourceRect(new float[]{0f, -1f}), 0.0001f);
    }
}
