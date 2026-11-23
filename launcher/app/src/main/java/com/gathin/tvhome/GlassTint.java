package com.gathin.tvhome;

/**
 * The tint and contrast rules of a glass panel (HOME-DESIGN.md 19.3 and 19.8). Pure: the tests assert that text keeps
 * 4.5:1 whatever is behind the panel, so contrast is guaranteed by construction and not by hoping the picture is dark.
 */
final class GlassTint {
    private GlassTint() { }

    /** Which material a panel is drawn in. */
    static final int MODE_SOLID = 0, MODE_TINTED = 1, MODE_GLASS = 2;

    /** Tint colours (opaque RGB) of the three materials, 19.3 layer 2. */
    static final int GLASS_TINT = 0xFF0B0D12, TINTED_TINT = 0xFF0E1016, SOLID_TINT = 0xFF16181D;
    /** Fixed tint alpha of the Tinted material; Solid is 1. */
    static final float TINTED_ALPHA = 0.78f, SOLID_ALPHA = 1f;
    /** Strength of the lens band at the top edge, 19.3 layer 3: Glass is {@code GLASS_BAND * (1 - peak)}. */
    static final float GLASS_BAND = 0.14f, TINTED_BAND = 0.08f, SOLID_BAND = 0.05f;
    /** The band falls to 0 at this fraction of the height; the bottom band is a third as strong over the last 12 %. */
    static final float BAND_TOP_END = 0.28f, BAND_BOTTOM_START = 0.88f, BAND_BOTTOM_RATIO = 1f / 3f;
    /** Text on a panel: primary and secondary (78 % white) and the ink on a focused capsule, 19.5. */
    static final int PRIMARY_TEXT = 0xFFF5F7FA, SECONDARY_TEXT = 0xC7FFFFFF, FOCUS_INK = 0xFF101216;
    /** Selection ice blue and the inner shadow, 19.3 and 19.5. */
    static final int SELECTED = 0xFF8FC1FF, INNER_SHADOW = 0x40000000;
    /** Rim gradient stops (19.3 layer 5) and the flat Solid rim. */
    static final int[] RIM_COLORS = {0x8CFFFFFF, 0x26FFFFFF, 0x14FFFFFF, 0x4DFFFFFF};
    static final float[] RIM_STOPS = {0f, 0.35f, 0.65f, 1f};
    static final int SOLID_RIM = 0x33FFFFFF;
    /** The backdrop's grid for {@link #peak}. */
    static final int GRID = 4;

    /** Which material: Glass needs the setting, no reduction and Home behind; Tinted is glass over an app; Solid otherwise. */
    static int mode(boolean glassSurfaces, boolean reduceTransparency, boolean homeVisible) {
        if (!glassSurfaces || reduceTransparency) return MODE_SOLID;
        return homeVisible ? MODE_GLASS : MODE_TINTED;
    }

    /** Relative luminance of an ARGB colour, 0 to 1: channels in linear light ((c/255)^2.2), weights 0.2126 / 0.7152 / 0.0722. */
    static float luma(int argb) {
        return 0.2126f * lin((argb >> 16) & 0xFF) + 0.7152f * lin((argb >> 8) & 0xFF) + 0.0722f * lin(argb & 0xFF);
    }

    private static float lin(int c) {
        return (float) Math.pow(c / 255.0, 2.2);
    }

    /**
     * The brightest part of {@code px} (w by h) inside the rectangle [left,right) x [top,bottom): the region, clamped to the
     * image, is split into a 4x4 grid and the highest mean {@link #luma} of any cell is returned. The brightest cell, not
     * the average, so a panel straddling a bright limb and black space is tinted for the limb. An empty region (no backdrop
     * under the panel) answers 1, the worst case, so the tint stays safe.
     */
    static float peak(int[] px, int w, int h, int left, int top, int right, int bottom) {
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(w, right);
        bottom = Math.min(h, bottom);
        if (px == null || right <= left || bottom <= top) return 1f;
        float best = 0f;
        for (int gy = 0; gy < GRID; gy++) {
            int y0 = top + (bottom - top) * gy / GRID, y1 = top + (bottom - top) * (gy + 1) / GRID;
            for (int gx = 0; gx < GRID; gx++) {
                int x0 = left + (right - left) * gx / GRID, x1 = left + (right - left) * (gx + 1) / GRID;
                if (x1 <= x0 || y1 <= y0) continue;
                double sum = 0;
                for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) sum += luma(px[y * w + x]);
                best = Math.max(best, (float) (sum / ((x1 - x0) * (y1 - y0))));
            }
        }
        return best;
    }

    /**
     * Glass tint alpha: 0.40 over black, 0.72 over white, rising steeply at first and then gently
     * ({@code 0.40 + 0.32 * sqrt(peak)}). The straight line of the first draft of 19.3 dips to 4.3:1 for secondary text over a
     * mid grey with the lens band at full strength; the square root keeps both ends and clears 4.5:1 everywhere.
     */
    static float tintAlpha(float peak) {
        return 0.40f + 0.32f * (float) Math.sqrt(clamp01(peak));
    }

    /** Strength of the Glass lens band at the top edge: {@code 0.14 * (1 - peak)}. */
    static float bandAlpha(float peak) {
        return GLASS_BAND * (1f - clamp01(peak));
    }

    /** {@code fg} (its RGB) laid over {@code bg} at {@code alpha} (0 to 1), as an opaque ARGB. */
    static int over(int fg, float alpha, int bg) {
        float a = clamp01(alpha);
        int r = mix((fg >> 16) & 0xFF, (bg >> 16) & 0xFF, a);
        int g = mix((fg >> 8) & 0xFF, (bg >> 8) & 0xFF, a);
        int b = mix(fg & 0xFF, bg & 0xFF, a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int f, int b, float a) {
        return Math.round(f * a + b * (1f - a));
    }

    /** WCAG 2.2 contrast ratio of two opaque colours, 1 to 21. */
    static double contrast(int a, int b) {
        double la = wcag(a), lb = wcag(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double wcag(int argb) {
        return 0.2126 * wcagChannel((argb >> 16) & 0xFF) + 0.7152 * wcagChannel((argb >> 8) & 0xFF) + 0.0722 * wcagChannel(argb & 0xFF);
    }

    private static double wcagChannel(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
