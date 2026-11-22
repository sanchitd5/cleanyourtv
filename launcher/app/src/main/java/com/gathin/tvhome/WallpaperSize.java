package com.gathin.tvhome;

/**
 * How big a theme's wallpaper may be and what size it is decoded at (#166). A 1 GB TV cannot afford a
 * picture decoded at full size: a 65535x2159 image is about 283 MB even in RGB_565.
 */
final class WallpaperSize {
    private WallpaperSize() { }

    /** 4K UHD (3840x2160, 8.3 M pixels) and a little more; anything bigger is refused. */
    static final long MAX_PIXELS = 8_400_000L;

    static boolean accepted(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }

    /** The largest power-of-two sample size that still covers the screen in both directions. */
    static int sampleSize(int width, int height, int screenW, int screenH) {
        int sample = 1;
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2;
        return sample;
    }

    /**
     * {inDensity, inTargetDensity} that scale a (sampled) picture down to what CENTER_CROP shows: the
     * side that limits the cover lands on the screen size. Null when it is already that small.
     */
    static int[] coverScale(int width, int height, int screenW, int screenH) {
        if ((long) screenW * height >= (long) screenH * width) {   // width limits the cover
            return width > screenW ? new int[]{width, screenW} : null;
        }
        return height > screenH ? new int[]{height, screenH} : null;
    }

    /**
     * wallpaper_video: a full-screen VideoView stretches a clip to its own shape, so this is the {x, y} scale about
     * the centre that puts the clip's shape back and fills the view, cutting what hangs over (a centre crop).
     * {1, 1} for a clip of the view's shape, or while either size is unknown.
     */
    static float[] cropScale(int width, int height, int viewW, int viewH) {
        if (width <= 0 || height <= 0 || viewW <= 0 || viewH <= 0) return new float[]{1f, 1f};
        float cover = Math.max((float) viewW / width, (float) viewH / height);
        return new float[]{width * cover / viewW, height * cover / viewH};
    }

    /**
     * Which part of the uncropped clip frame is on screen, as {left, top, right, bottom} fractions of the frame (0 to 1):
     * the VideoView stretches the frame to the view and {@link #cropScale} then magnifies it about the centre, so the
     * visible part is the centred 1/scale of each axis. The full frame, {0, 0, 1, 1}, for a clip of the view's shape or an
     * unknown scale. G2 uses it to turn a screen rectangle into a PixelCopy source rectangle.
     */
    static float[] sourceRect(float[] cropScale) {
        float sx = cropScale != null && cropScale.length >= 2 && cropScale[0] >= 1f ? cropScale[0] : 1f;
        float sy = cropScale != null && cropScale.length >= 2 && cropScale[1] >= 1f ? cropScale[1] : 1f;
        return new float[]{0.5f - 0.5f / sx, 0.5f - 0.5f / sy, 0.5f + 0.5f / sx, 0.5f + 0.5f / sy};
    }
}
