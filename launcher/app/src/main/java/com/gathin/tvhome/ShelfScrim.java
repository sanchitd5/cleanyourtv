package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

/**
 * The two fixed gradients behind the top shelf (HOME-DESIGN.md 3.5): the alpha curves (pure, tested) and the drawable
 * that paints them. Strengths are set so white text at 90 % (the shelf, clock, date and caption inks) keeps 4.5:1 over a
 * pure white frame: that needs a scrim of about 0.57 where small text sits, so the date's corner holds 0.62 or more.
 */
final class ShelfScrim {
    private ShelfScrim() {}

    /** Height fractions and the ramp's alpha at each, linear between them. */
    private static final float[] RAMP_Y = {0f, 0.42f, 0.52f, 0.60f, 0.70f, 1f};
    private static final float[] RAMP_A = {0f, 0f, 0.44f, 0.62f, 0.68f, 0.82f};

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

    /** Alpha of the corner gradient at a distance from the corner as a fraction of its radius: 0.66 at 0, 0.62 at 0.6 (where the date ends), 0 at 1 and beyond. */
    static float cornerAlpha(float r) {
        if (r <= 0f) return 0.66f;
        if (r >= 1f) return 0f;
        return r <= 0.6f ? 0.66f + (0.62f - 0.66f) * (r / 0.6f) : 0.62f * (1f - (r - 0.6f) / 0.4f);
    }

    /** {@link #cornerAlpha(float)} scaled by top_shelf_scrim. */
    static float cornerAlpha(float r, int percent) {
        return cornerAlpha(r) * clampPercent(percent) / 100f;
    }

    /** The scrim tint, near-black with a little blue. */
    private static final int TINT = 0x05070C;
    private static final int SIZE = 64;

    /**
     * Layer 0 the ramp, layer 1 the corner: two 64x64 bitmaps stretched over the view, no blur. The corner is on the
     * top-right when rtl. percent is top_shelf_scrim.
     */
    static LayerDrawable drawable(Resources res, int percent, boolean rtl) {
        Bitmap ramp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setDither(true);
        int n = RAMP_Y.length;
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) colors[i] = color(rampAlpha(RAMP_Y[i], percent));
        paint.setShader(new LinearGradient(0, 0, 0, SIZE, colors, RAMP_Y, Shader.TileMode.CLAMP));
        new Canvas(ramp).drawRect(0, 0, SIZE, SIZE, paint);

        Bitmap corner = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        float[] stops = {0f, 0.3f, 0.6f, 0.8f, 1f};
        int[] cc = new int[stops.length];
        for (int i = 0; i < stops.length; i++) cc[i] = color(cornerAlpha(stops[i], percent));
        paint.setShader(new RadialGradient(rtl ? SIZE : 0, 0, 0.55f * SIZE, cc, stops, Shader.TileMode.CLAMP));
        new Canvas(corner).drawRect(0, 0, SIZE, SIZE, paint);

        BitmapDrawable a = new BitmapDrawable(res, ramp), b = new BitmapDrawable(res, corner);
        a.setGravity(Gravity.FILL);
        b.setGravity(Gravity.FILL);
        a.setFilterBitmap(true);
        b.setFilterBitmap(true);
        return new LayerDrawable(new Drawable[]{a, b});
    }

    private static int color(float alpha) {
        return Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24 | TINT;
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
