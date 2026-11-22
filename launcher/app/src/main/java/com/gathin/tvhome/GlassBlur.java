package com.gathin.tvhome;

/**
 * The blur behind a glass panel (HOME-DESIGN.md 19.4): pure integer code on small ARGB arrays, so it runs in a few
 * milliseconds off the main thread and needs neither RenderScript nor a GPU pass. No android.graphics.Color.
 */
final class GlassBlur {
    private GlassBlur() { }

    /** Box radius, passes and saturation of {@link #soften}: three passes of radius 3 are about a 3.5 px gaussian. */
    static final int RADIUS = 3, PASSES = 3;
    /** Saturation 1.4 as a fraction, so the arithmetic stays integer. */
    private static final int SAT_NUM = 7, SAT_DEN = 5;

    /**
     * One pass of a box blur along one axis, edges clamped (the edge pixel repeats). {@code src} and {@code dst} must be
     * different arrays of at least w*h ints. The result's alpha is always 0xFF.
     */
    static void box(int[] src, int[] dst, int w, int h, int radius, boolean horizontal) {
        if (radius < 0) radius = 0;
        int taps = 2 * radius + 1;
        int lines = horizontal ? h : w;
        int length = horizontal ? w : h;
        int step = horizontal ? 1 : w;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * w : line;
            int r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int p = src[base + clamp(k, length) * step];
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                dst[base + i * step] = 0xFF000000 | ((r + taps / 2) / taps << 16) | ((g + taps / 2) / taps << 8) | ((b + taps / 2) / taps);
                int out = src[base + clamp(i - radius, length) * step];
                int in = src[base + clamp(i + radius + 1, length) * step];
                r += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += (in & 0xFF) - (out & 0xFF);
            }
        }
    }

    /** Saturation x1.4, then three horizontal-plus-vertical box passes of radius 3. {@code scratch} is as long as {@code px}. */
    static void soften(int[] px, int[] scratch, int w, int h) {
        saturate(px, w * h);
        for (int pass = 0; pass < PASSES; pass++) {
            box(px, scratch, w, h, RADIUS, true);
            box(scratch, px, w, h, RADIUS, false);
        }
    }

    /** Pushes each channel away from the pixel's grey by 1.4 (Rec. 709 integer weights), clamped; alpha becomes 0xFF. */
    static void saturate(int[] px, int count) {
        for (int i = 0; i < count; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
            int grey = (r * 54 + g * 183 + b * 19) >> 8;
            r = clamp255(grey + (r - grey) * SAT_NUM / SAT_DEN);
            g = clamp255(grey + (g - grey) * SAT_NUM / SAT_DEN);
            b = clamp255(grey + (b - grey) * SAT_NUM / SAT_DEN);
            px[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    private static int clamp(int i, int length) {
        return i < 0 ? 0 : i >= length ? length - 1 : i;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
