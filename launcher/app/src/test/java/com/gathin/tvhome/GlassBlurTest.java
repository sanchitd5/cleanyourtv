package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GlassBlurTest {
    private static int gray(int v) {
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    @Test public void aFlatFieldStaysFlat() {
        int w = 16, h = 9;
        int[] src = new int[w * h], dst = new int[w * h];
        Arrays.fill(src, 0xFF336699);
        GlassBlur.box(src, dst, w, h, 3, true);
        for (int p : dst) assertEquals(0xFF336699, p);
        GlassBlur.box(dst, src, w, h, 3, false);
        for (int p : src) assertEquals(0xFF336699, p);
    }

    @Test public void anImpulseSpreadsSymmetricallyAndKeepsItsSum() {
        int w = 21, h = 1;
        int[] src = new int[w], dst = new int[w];
        Arrays.fill(src, gray(0));
        src[10] = gray(210);
        GlassBlur.box(src, dst, w, h, 3, true);
        int sum = 0;
        for (int x = 0; x < w; x++) {
            assertEquals(dst[10 - Math.min(x, 10)] & 0xFF, dst[10 + Math.min(x, 10)] & 0xFF);
            sum += dst[x] & 0xFF;
        }
        assertEquals(210, sum, 7);          // rounding of 7 taps
        assertEquals(30, dst[10] & 0xFF);
        assertEquals(30, dst[7] & 0xFF);
        assertEquals(0, dst[6] & 0xFF);
    }

    @Test public void edgesClampSoABrightEdgeStaysBright() {
        int w = 8, h = 1;
        int[] src = new int[w], dst = new int[w];
        Arrays.fill(src, gray(200));
        GlassBlur.box(src, dst, w, h, 3, true);
        assertEquals(200, dst[0] & 0xFF);
        assertEquals(200, dst[w - 1] & 0xFF);
        src[0] = gray(0);
        GlassBlur.box(src, dst, w, h, 1, true);
        assertEquals(67, dst[0] & 0xFF);
    }

    @Test public void verticalPassBlursColumns() {
        int w = 1, h = 9;
        int[] src = new int[h], dst = new int[h];
        Arrays.fill(src, gray(0));
        src[4] = gray(90);
        GlassBlur.box(src, dst, w, h, 1, false);
        assertEquals(30, dst[3] & 0xFF);
        assertEquals(30, dst[4] & 0xFF);
        assertEquals(30, dst[5] & 0xFF);
        assertEquals(0, dst[2] & 0xFF);
    }

    @Test public void alphaIsAlwaysOpaque() {
        int w = 6, h = 6;
        int[] src = new int[w * h], dst = new int[w * h];
        Arrays.fill(src, 0x00123456);
        GlassBlur.box(src, dst, w, h, 2, true);
        for (int p : dst) assertEquals(0xFF, p >>> 24);
        GlassBlur.soften(src, dst, w, h);
        for (int p : src) assertEquals(0xFF, p >>> 24);
    }

    @Test public void softenSaturatesAndKeepsGreyAsItWas() {
        int[] px = new int[16 * 9], scratch = new int[16 * 9];
        Arrays.fill(px, gray(120));
        GlassBlur.soften(px, scratch, 16, 9);
        for (int p : px) assertEquals(gray(120), p);
        int[] one = {0xFF806040};
        GlassBlur.saturate(one, 1);
        assertTrue(((one[0] >> 16) & 0xFF) > 0x80);
        assertTrue((one[0] & 0xFF) < 0x40);
    }

    @Test public void softenSpreadsAHardEdge() {
        int w = 128, h = 72;
        int[] px = new int[w * h], scratch = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = x < w / 2 ? gray(255) : gray(0);
        GlassBlur.soften(px, scratch, w, h);
        int mid = (h / 2) * w;
        assertTrue((px[mid + w / 2 - 12] & 0xFF) > (px[mid + w / 2 + 12] & 0xFF));
        int between = px[mid + w / 2] & 0xFF;
        assertTrue(between > 60 && between < 200);
    }
}
