package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import java.util.function.BooleanSupplier;

/**
 * Home's staged recede while nobody presses a key (HOME-DESIGN.md 7.3): calm (the shelf fades, the ramp scrim relaxes),
 * orbit (the header and caption glide a few dp), dim (header, dock and glass to 60 %). Only alpha and translation change,
 * never focus, scrolling or the wallpaper. poke() on any key brings everything back in 180 ms. The schedule is
 * {@link StillGuard}; this class only owns the timers and the views.
 */
final class HomeIdle {
    private static final float DIM = 0.60f, CALM_RAMP = 0.60f;
    private static final long WAKE_MS = 180, CALM_MS = 700, ORBIT_MS = 2000, DIM_MS = 3000;

    private final long calmMs, dimMs;
    private final boolean shift, reduced;
    private final float density;
    private final View header, hero, grid, glass, caption;
    private final Drawable ramp;
    private final BooleanSupplier heroShown;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ValueAnimator rampAnimator = new ValueAnimator();
    private final PathInterpolator standard = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private final DecelerateInterpolator easeOut = new DecelerateInterpolator(2f);

    private boolean running;
    private long since;
    private int applied;            // StillGuard.CALM / DIM bits applied
    private int orbitStep = -1;     // last orbit step applied
    private View watermark;         // watermark: the credit line, or null
    private boolean watermarkGone;  // watermark: faded out by the calm stage
    private boolean captionBaseKnown;
    private float captionBaseX, captionBaseY;
    private int rampBase = 255;

    private final Runnable tick = this::advance;

    /** ramp and caption may be null; heroShown tells whether the shelf is meant to be visible (a dock tile has focus). */
    HomeIdle(Config cfg, float density, boolean reduced, View header, View hero, View grid, View glass, View caption,
             Drawable ramp, int rampBaseAlpha, BooleanSupplier heroShown) {
        this.calmMs = cfg.homeIdleCalmSeconds * 1000L;
        this.dimMs = cfg.homeIdleDimSeconds * 1000L;
        this.shift = cfg.homeIdleShift;
        this.reduced = reduced;
        this.density = density;
        this.header = header;
        this.hero = hero;
        this.grid = grid;
        this.glass = glass;
        this.caption = caption;
        this.ramp = ramp;
        this.rampBase = rampBaseAlpha;
        this.heroShown = heroShown;
        rampAnimator.addUpdateListener(a -> ramp().setAlpha((int) a.getAnimatedValue()));
    }

    /** watermark: the credit line drifts with the header and fades at the calm stage. */
    void watermark(View view) {
        watermark = view;
    }

    /** True when the config asks for any stage at all. */
    static boolean wanted(Config cfg) {
        return cfg.homeIdleCalmSeconds > 0 || cfg.homeIdleDimSeconds > 0 || cfg.homeIdleShift
                || !cfg.watermark.isEmpty();
    }

    private Drawable ramp() {
        return ramp;
    }

    /** Home is in front: count idle time from now. */
    void start() {
        running = true;
        since = SystemClock.uptimeMillis();
        schedule();
    }

    /** Home left the front: stop and put everything back at once. */
    void stop() {
        running = false;
        handler.removeCallbacks(tick);
        cancelAnimations();
        restore(0);
    }

    /** A key or touch: restore whatever a stage changed, then count again. The key itself is not consumed. */
    void poke() {
        if (!running) return;
        handler.removeCallbacks(tick);
        if (applied != 0 || orbitStep >= 0 || watermarkGone) restore(reduced ? 90 : WAKE_MS);
        since = SystemClock.uptimeMillis();
        schedule();
    }

    private void schedule() {
        long idle = SystemClock.uptimeMillis() - since;
        long next = Long.MAX_VALUE;
        if (calmMs > 0 && (applied & StillGuard.CALM) == 0) next = Math.min(next, calmMs);
        if (dimMs > 0 && (applied & StillGuard.DIM) == 0) next = Math.min(next, dimMs);
        if (watermark != null && !watermarkGone) next = Math.min(next, Watermark.hideAfterMs(calmMs, dimMs));   // watermark
        if (shift) next = Math.min(next, StillGuard.ORBIT_START_MS + (orbitStep + 1) * StillGuard.ORBIT_EVERY_MS);
        if (next == Long.MAX_VALUE) return;
        handler.postDelayed(tick, Math.max(0L, next - idle));
    }

    private void advance() {
        if (!running) return;
        long idle = SystemClock.uptimeMillis() - since;
        int stage = StillGuard.homeStage(idle, calmMs, dimMs);
        if ((stage & StillGuard.CALM) != 0 && (applied & StillGuard.CALM) == 0) calm();
        if ((stage & StillGuard.DIM) != 0 && (applied & StillGuard.DIM) == 0) dim();
        applied |= stage;
        if (watermark != null && !watermarkGone && idle >= Watermark.hideAfterMs(calmMs, dimMs)) {   // watermark
            watermarkGone = true;
            watermark.animate().alpha(0f).setDuration(ms(CALM_MS)).setInterpolator(standard).start();
        }
        if (shift) {
            int step = StillGuard.orbitStep(idle);
            if (step > orbitStep) orbit(step);
        }
        schedule();
    }

    private long ms(long normal) {
        return reduced ? 90 : normal;
    }

    private void calm() {
        hero.animate().cancel();
        hero.animate().alpha(0f).setDuration(ms(CALM_MS)).setInterpolator(standard).start();
        if (ramp != null) {
            rampAnimator.cancel();
            rampAnimator.setIntValues(ramp.getAlpha(), Math.round(rampBase * CALM_RAMP));
            rampAnimator.setDuration(ms(CALM_MS));
            rampAnimator.setInterpolator(standard);
            rampAnimator.start();
        }
    }

    private void dim() {
        long d = ms(DIM_MS);
        LinearInterpolator linear = new LinearInterpolator();
        header.animate().alpha(DIM).setDuration(d).setInterpolator(linear).start();
        grid.animate().alpha(DIM).setDuration(d).setInterpolator(linear).withLayer().start();
        if (glass.getAlpha() > 0.01f) glass.animate().alpha(DIM).setDuration(d).setInterpolator(linear).start();
    }

    private void orbit(int step) {
        if (caption != null && !captionBaseKnown) {
            captionBaseX = caption.getTranslationX();
            captionBaseY = caption.getTranslationY();
            captionBaseKnown = true;
        }
        orbitStep = step;
        float[] o = StillGuard.orbit(step);
        float x = o[0] * density, y = o[1] * density;
        header.animate().translationX(x).translationY(y).setDuration(ms(ORBIT_MS)).setInterpolator(standard).start();
        if (watermark != null) watermark.animate().translationX(x).translationY(y).setDuration(ms(ORBIT_MS)).setInterpolator(standard).start();   // watermark
        if (caption != null) {
            caption.animate().translationX(captionBaseX + x).translationY(captionBaseY + y)
                    .setDuration(ms(ORBIT_MS)).setInterpolator(standard).start();
        }
    }

    private void cancelAnimations() {
        rampAnimator.cancel();
        hero.animate().cancel();
        header.animate().cancel();
        if (watermark != null) watermark.animate().cancel();   // watermark
        grid.animate().cancel();
        glass.animate().cancel();
        if (caption != null) caption.animate().cancel();
    }

    /** Everything back to full strength over ms (0 snaps), and the stages armed again. */
    private void restore(long ms) {
        boolean dimmed = (applied & StillGuard.DIM) != 0, moved = orbitStep >= 0, calmed = (applied & StillGuard.CALM) != 0;
        boolean wmGone = watermarkGone;   // watermark
        applied = 0;
        orbitStep = -1;
        watermarkGone = false;
        if (!dimmed && !moved && !calmed && !wmGone) return;
        cancelAnimations();
        boolean glassUp = glass.getAlpha() > 0.01f;
        if (ms <= 0) {
            header.setAlpha(1f);
            header.setTranslationX(0f);
            header.setTranslationY(0f);
            if (watermark != null) {   // watermark
                watermark.setAlpha(1f);
                watermark.setTranslationX(0f);
                watermark.setTranslationY(0f);
            }
            grid.setAlpha(1f);
            if (glassUp) glass.setAlpha(1f);
            if (calmed && heroShown.getAsBoolean()) hero.setAlpha(1f);
            if (caption != null && captionBaseKnown) {
                caption.setTranslationX(captionBaseX);
                caption.setTranslationY(captionBaseY);
            }
            if (ramp != null) ramp.setAlpha(rampBase);
        } else {
            if (dimmed) {
                header.animate().alpha(1f).setDuration(ms).setInterpolator(easeOut).start();
                grid.animate().alpha(1f).setDuration(ms).setInterpolator(easeOut).withLayer().start();
                if (glassUp) glass.animate().alpha(1f).setDuration(ms).setInterpolator(easeOut).start();
            }
            if (watermark != null) {   // watermark
                watermark.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(ms).setInterpolator(easeOut).start();
            }
            if (moved) {
                header.animate().translationX(0f).translationY(0f).setDuration(ms).setInterpolator(easeOut).start();
                if (caption != null && captionBaseKnown) {
                    caption.animate().translationX(captionBaseX).translationY(captionBaseY).setDuration(ms)
                            .setInterpolator(easeOut).start();
                }
            }
            if (calmed) {
                if (heroShown.getAsBoolean()) hero.animate().alpha(1f).setDuration(ms).setInterpolator(easeOut).start();
                if (ramp != null) {
                    rampAnimator.setIntValues(ramp.getAlpha(), rampBase);
                    rampAnimator.setDuration(ms);
                    rampAnimator.setInterpolator(easeOut);
                    rampAnimator.start();
                }
            }
        }
        captionBaseKnown = false;
    }
}
