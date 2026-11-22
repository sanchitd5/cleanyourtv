package com.gathin.tvhome;

/** Makes an app's sampled colour usable as a glow and a shelf tick on a dark picture (HOME-DESIGN.md 5.6). Pure integer and float maths. */
final class Accent {
    static final float MIN_VALUE = 0.75f, MAX_SATURATION = 0.85f;

    private Accent() {}

    /** The colour with its HSV value raised to at least 0.75, its saturation capped at 0.85 and alpha 0xFF; a grey stays grey. */
    static int legible(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        float v = Math.max(max, MIN_VALUE);
        if (delta == 0f) {
            int grey = Math.round(v * 255f);
            return 0xFF000000 | grey << 16 | grey << 8 | grey;
        }
        float s = Math.min(delta / max, MAX_SATURATION);
        float h;
        if (max == r) h = ((g - b) / delta) % 6f;
        else if (max == g) h = (b - r) / delta + 2f;
        else h = (r - g) / delta + 4f;
        if (h < 0f) h += 6f;
        float c = v * s, x = c * (1f - Math.abs(h % 2f - 1f)), m = v - c;
        float rr, gg, bb;
        switch ((int) h) {
            case 0: rr = c; gg = x; bb = 0; break;
            case 1: rr = x; gg = c; bb = 0; break;
            case 2: rr = 0; gg = c; bb = x; break;
            case 3: rr = 0; gg = x; bb = c; break;
            case 4: rr = x; gg = 0; bb = c; break;
            default: rr = c; gg = 0; bb = x; break;
        }
        return 0xFF000000 | Math.round((rr + m) * 255f) << 16 | Math.round((gg + m) * 255f) << 8 | Math.round((bb + m) * 255f);
    }
}
