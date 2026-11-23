package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

/** Builds the still wallpaper's backdrop copy in two filtered steps (HOME-DESIGN.md 19.4). Pure bitmap work; no state. */
final class GlassStill {
    private GlassStill() { }

    private static final int MID_W = 480, MID_H = 270;

    /** Step 1: the part of a software picture that the screen shows, filtered down to 480x270. Null on failure. */
    static Bitmap reduce(Bitmap picture, int screenW, int screenH) {
        try {
            int[] crop = new int[4];
            GlassSampling.centerCrop(picture.getWidth(), picture.getHeight(), screenW, screenH, crop);
            return draw(picture, new Rect(crop[0], crop[1], crop[2], crop[3]), MID_W, MID_H);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Step 2: 128x72, saturated and blurred, as a new ARGB_8888 bitmap. Recycles {@code mid}. Null on failure. */
    static Bitmap soften(Bitmap mid) {
        try {
            Bitmap small = draw(mid, null, GlassBackdrop.WIDTH, GlassBackdrop.HEIGHT);
            mid.recycle();
            int n = GlassBackdrop.WIDTH * GlassBackdrop.HEIGHT;
            int[] px = new int[n];
            small.getPixels(px, 0, GlassBackdrop.WIDTH, 0, 0, GlassBackdrop.WIDTH, GlassBackdrop.HEIGHT);
            small.recycle();
            GlassBlur.soften(px, new int[n], GlassBackdrop.WIDTH, GlassBackdrop.HEIGHT);
            return Bitmap.createBitmap(px, GlassBackdrop.WIDTH, GlassBackdrop.HEIGHT, Bitmap.Config.ARGB_8888);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private static Bitmap draw(Bitmap from, Rect src, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        new Canvas(out).drawBitmap(from, src, new Rect(0, 0, w, h), paint);
        return out;
    }
}
