package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

/**
 * The glass panel (HOME-DESIGN.md 19.3 and 19.5): a backdrop, a tint, a lens band, an inner shadow and a specular rim in one
 * rounded rectangle, in one of three materials chosen by {@link GlassTint#mode}. Nothing is allocated in {@link #draw}: the
 * paints, shaders, path and matrices are built in the constructor, on a bounds change or when the backdrop changes. There is no
 * blur here; the backdrop is the 128x72 copy {@link GlassBackdrop} already blurred.
 */
final class GlassPanelDrawable extends Drawable implements GlassBackdrop.Listener {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final float density;
    private final float radius;
    private boolean glassOn, reduceTransparency, homeHosted;
    private int mode = GlassTint.MODE_SOLID;

    private final Paint backdropPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF(), shadowBox = new RectF(), rimBox = new RectF();
    private final Matrix backdropMatrix = new Matrix(), bandMatrix = new Matrix(), rimMatrix = new Matrix();
    private final float[] align = new float[4], line = new float[4];
    private final int[] region = new int[4];
    private final int[] pixels = new int[GlassBackdrop.WIDTH * GlassBackdrop.HEIGHT];
    private Shader topBand, bottomBand, rim;
    private Bitmap backdrop;
    private Bitmap fadeFrom;   // the backdrop being faded out, while a cross-fade runs
    private boolean fadeAllowed;
    private final ValueAnimator fader = ValueAnimator.ofFloat(0f, 1f);
    private float peak = 1f;
    private float bias;
    private boolean positioned;
    private float screenLeft, screenTop, screenW, screenH;
    private float bandTopBottom, bandBottomTop;
    private int seenGeneration = -1;
    private int fadeAlpha = 255;
    private final Runnable refreshNow = () -> {
        if (seenGeneration == GlassBackdrop.generation()) return;
        refresh();
        invalidateSelf();
    };

    GlassPanelDrawable(float radiusPx, float density) {
        this.radius = radiusPx;
        this.density = density;
        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStrokeWidth(GlassSurface.LINE_DP * density);
        shadowPaint.setColor(GlassTint.INNER_SHADOW);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(GlassSurface.LINE_DP * density);
        fader.setInterpolator(new android.view.animation.LinearInterpolator());
        fader.addUpdateListener(a -> setCrossfade(a.getAnimatedFraction()));
        fader.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                setCrossfade(1f);
            }
        });
        GlassBackdrop.addListener(this);   // weak: the view that holds this drawable keeps it alive
    }

    /** The settings that pick the material (19.3): glass_surfaces, reduce transparency, and whether the panel lives in Home. */
    void setPolicy(boolean glassSurfaces, boolean reduceTransparency, boolean homeHosted) {
        glassOn = glassSurfaces;
        this.reduceTransparency = reduceTransparency;
        this.homeHosted = homeHosted;
        refresh();
        invalidateSelf();
    }

    /** Where the panel sits on screen (read on layout, not per frame) and the screen's size, for the backdrop's alignment. */
    void setScreenPosition(float left, float top, float screenW, float screenH) {
        if (positioned && left == screenLeft && top == screenTop && screenW == this.screenW && screenH == this.screenH) return;
        screenLeft = left;
        screenTop = top;
        this.screenW = screenW;
        this.screenH = screenH;
        positioned = screenW > 0f && screenH > 0f;
        refresh();
        invalidateSelf();
    }

    /**
     * Where the focused row sits in the panel, -1 (top) to 1 (bottom): the lens band shifts by bias * 5 dp and the rim turns by
     * bias * 10 degrees (19.5). The caller animates it (G4); this only applies the value, with no allocation.
     */
    void setHighlightOffset(float bias) {
        float b = GlassSurface.clampBias(bias);
        if (b == this.bias) return;
        this.bias = b;
        applyBias();
        invalidateSelf();
    }

    /** Whether a new backdrop fades in (animations on, reduced motion off); off, it simply replaces the old one. */
    void setFadeAllowed(boolean allowed) {
        fadeAllowed = allowed;
    }

    /**
     * How far the new backdrop has faded in over the old, 0 to 1 (19.7). 1 ends the fade and lets go of the old bitmap. Driven by
     * the drawable's own animator, which follows the system animator scale; allocation-free.
     */
    void setCrossfade(float fraction) {
        fadeAlpha = GlassMotion.fadeAlpha255(fraction);
        if (fadeAlpha >= 255) fadeFrom = null;
        invalidateSelf();
    }

    int mode() {
        return mode;
    }

    @Override
    public void onBackdropChanged() {
        if (seenGeneration == GlassBackdrop.generation()) return;
        MAIN.post(refreshNow);   // the publisher may be the wallpaper thread; drawables are touched on the main thread
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        box.set(bounds);
        float r = GlassSurface.fitRadius(radius, box.width(), box.height());
        clip.reset();
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        float s = GlassSurface.SHADOW_INSET_DP * density, half = GlassSurface.LINE_DP * density / 2f;
        shadowBox.set(box.left + s + half, box.top + s + half, box.right - s - half, box.bottom - s - half);
        rimBox.set(box.left + half, box.top + half, box.right - half, box.bottom - half);
        refresh();
    }

    /** Re-reads the mode and the backdrop and rebuilds everything that depends on them. Never called from draw(). */
    private void refresh() {
        seenGeneration = GlassBackdrop.generation();
        mode = GlassTint.mode(glassOn, reduceTransparency, homeHosted || GlassBackdrop.homeVisible());
        Bitmap b = mode == GlassTint.MODE_GLASS ? GlassBackdrop.current() : null;
        if (b != null && (b.getWidth() != GlassBackdrop.WIDTH || b.getHeight() != GlassBackdrop.HEIGHT)) b = null;
        if (fadeAllowed && backdrop != null && b != null && b != backdrop && positioned) {
            // A new picture or live sample arrives while one is on show: fade from the old (a copy of the reference, no pixels).
            fader.cancel();
            fadeFrom = backdrop;
            fader.setDuration(GlassMotion.fadeMs(backdrop != GlassBackdrop.still()));
            fadeAlpha = 0;
            backdrop = b;
            fader.start();
        } else if (b != backdrop) {
            fader.cancel();
            fadeFrom = null;
            fadeAlpha = 255;
        }
        backdrop = b;
        float w = box.width(), h = box.height();
        peak = 1f;
        if (GlassSurface.drawsBackdrop(mode, b != null, positioned) && w > 0f && h > 0f) {
            b.getPixels(pixels, 0, GlassBackdrop.WIDTH, 0, 0, GlassBackdrop.WIDTH, GlassBackdrop.HEIGHT);
            GlassBackdrop.regionUnder(screenLeft, screenTop, w, h, screenW, screenH, region);
            peak = GlassTint.peak(pixels, GlassBackdrop.WIDTH, GlassBackdrop.HEIGHT, region[0], region[1], region[2], region[3]);
            GlassBackdrop.align(screenLeft, screenTop, w, h, screenW, screenH, GlassBackdrop.MAGNIFY, align);
            backdropMatrix.setScale(align[0], align[1]);
            backdropMatrix.postTranslate(align[2] + box.left, align[3] + box.top);
        }
        tintPaint.setColor(GlassSurface.withAlpha(GlassSurface.tintColor(mode), GlassSurface.tintAlpha(mode, peak)));
        if (w > 0f && h > 0f) buildShaders(w, h);
        applyBias();
    }

    private void buildShaders(float w, float h) {
        int white = 0xFFFFFF;
        bandTopBottom = GlassSurface.bandTopEnd(h) + GlassSurface.bandShiftPx(1f, density);
        bandBottomTop = GlassSurface.bandBottomStart(h) - GlassSurface.bandShiftPx(1f, density);
        topBand = new LinearGradient(0f, box.top, 0f, box.top + GlassSurface.bandTopEnd(h),
                GlassSurface.withAlpha(white, GlassSurface.bandAlpha(mode, peak)), GlassSurface.withAlpha(white, 0f), Shader.TileMode.CLAMP);
        bottomBand = new LinearGradient(0f, box.top + GlassSurface.bandBottomStart(h), 0f, box.bottom,
                GlassSurface.withAlpha(white, 0f), GlassSurface.withAlpha(white, GlassSurface.bottomBandAlpha(mode, peak)), Shader.TileMode.CLAMP);
        if (GlassSurface.flatRim(mode)) {
            rim = null;
            rimPaint.setShader(null);
            rimPaint.setColor(GlassTint.SOLID_RIM);
        } else {
            GlassSurface.gradientLine(w, h, GlassSurface.RIM_ANGLE, line);
            rim = new LinearGradient(box.left + line[0], box.top + line[1], box.left + line[2], box.top + line[3],
                    GlassTint.RIM_COLORS, GlassTint.RIM_STOPS, Shader.TileMode.CLAMP);
            rimPaint.setShader(rim);
        }
        bandPaint.setShader(topBand);
    }

    private void applyBias() {
        if (topBand == null) return;
        bandMatrix.setTranslate(0f, GlassSurface.bandShiftPx(bias, density));
        topBand.setLocalMatrix(bandMatrix);
        bottomBand.setLocalMatrix(bandMatrix);
        if (rim != null) {
            rimMatrix.setRotate(GlassSurface.rimTurnDegrees(bias), box.centerX(), box.centerY());
            rim.setLocalMatrix(rimMatrix);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (box.isEmpty()) return;
        float r = GlassSurface.fitRadius(radius, box.width(), box.height());
        int save = canvas.save();
        canvas.clipPath(clip);
        if (backdrop != null) {
            if (fadeFrom != null && fadeAlpha < 255) {
                backdropPaint.setAlpha(255);
                canvas.drawBitmap(fadeFrom, backdropMatrix, backdropPaint);
            }
            backdropPaint.setAlpha(fadeAlpha);
            canvas.drawBitmap(backdrop, backdropMatrix, backdropPaint);
        }
        canvas.drawRect(box, tintPaint);
        if (topBand != null) {
            bandPaint.setShader(topBand);
            canvas.drawRect(box.left, box.top, box.right, box.top + bandTopBottom, bandPaint);
            bandPaint.setShader(bottomBand);
            canvas.drawRect(box.left, box.top + bandBottomTop, box.right, box.bottom, bandPaint);
        }
        canvas.restoreToCount(save);
        canvas.drawRoundRect(shadowBox, GlassSurface.concentric(r, GlassSurface.SHADOW_INSET_DP * density),
                GlassSurface.concentric(r, GlassSurface.SHADOW_INSET_DP * density), shadowPaint);
        canvas.drawRoundRect(rimBox, r, r, rimPaint);
    }

    @Override
    public void setAlpha(int alpha) { }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
