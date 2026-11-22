package com.gathin.tvhome;

/**
 * The two fixed gradients behind the top shelf (HOME-DESIGN.md 3.5). Only the pure alpha curves are here; building the
 * drawable that paints them is part of the shelf step.
 */
final class ShelfScrim {
    private ShelfScrim() {}

    /** Height fractions and the ramp's alpha at each, linear between them. */
    private static final float[] RAMP_Y = {0f, 0.42f, 0.52f, 0.60f, 0.70f, 1f};
    private static final float[] RAMP_A = {0f, 0f, 0.40f, 0.56f, 0.64f, 0.80f};

    /** Alpha of the lower ramp at a height fraction (0 top, 1 bottom) at full strength, 0 to 0.80. */
    static float rampAlpha(float y) {
        if (y <= RAMP_Y[0]) return RAMP_A[0];
        for (int i = 1; i < RAMP_Y.length; i++) {
            if (y <= RAMP_Y[i]) {
                float t = (y - RAMP_Y[i - 1]) / (RAMP_Y[i] - RAMP_Y[i - 1]);
                return RAMP_A[i - 1] + t * (RAMP_A[i] - RAMP_A[i - 1]);
            }
        }
        return RAMP_A[RAMP_A.length - 1];
    }

    /** {@link #rampAlpha(float)} scaled by top_shelf_scrim, 0 to 100 percent. */
    static float rampAlpha(float y, int percent) {
        return rampAlpha(y) * clampPercent(percent) / 100f;
    }

    /** Alpha of the corner gradient at a distance from the corner as a fraction of its radius: 0.50 at 0, 0.28 at 0.5, 0 at 1 and beyond. */
    static float cornerAlpha(float r) {
        if (r <= 0f) return 0.50f;
        if (r >= 1f) return 0f;
        return r <= 0.5f ? 0.50f + (0.28f - 0.50f) * (r / 0.5f) : 0.28f * (1f - (r - 0.5f) / 0.5f);
    }

    /** {@link #cornerAlpha(float)} scaled by top_shelf_scrim. */
    static float cornerAlpha(float r, int percent) {
        return cornerAlpha(r) * clampPercent(percent) / 100f;
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
