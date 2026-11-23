package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/** The numbers behind Horizon's focus motion (HOME-DESIGN.md section 4): pure functions and two shared interpolators. */
final class FocusMotion {
    /** Shared so a focus change allocates nothing. */
    static final Interpolator EASE_OUT = new DecelerateInterpolator(1.5f);
    /** The reduced-motion zoom's own curve (factor 1), kept as it was. */
    static final Interpolator DECELERATE = new DecelerateInterpolator();
    static final Interpolator STANDARD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /** The banner's zoom with no parallax: today's 1.035. */
    static final float BASE_BANNER_SCALE = 1.035f;

    /** Shared overshoot curves for the zoom when focus_overshoot is on (advanced_effects and plain), and the tail-less one for a loss. */
    static final Interpolator OVERSHOOT_ADVANCED = new OvershootInterpolator(0.7f);
    static final Interpolator OVERSHOOT_PLAIN = new OvershootInterpolator(1.6f);
    static final Interpolator OVERSHOOT_NONE = new OvershootInterpolator(0f);

    /** The holder that gains focus within this long of a D-pad key reads that key's direction. */
    static final long KEY_WINDOW_MS = 300;
    /** Vertical parallax is this share of the horizontal one. */
    static final float VERTICAL_SHARE = 0.6f;
    /** Icon and input tiles move their glyph this share of the banner's distance. */
    static final float ICON_SHARE = 0.5f;

    private FocusMotion() {}

    /**
     * Screen-space x of a D-pad key: RIGHT is +1, LEFT is -1, whatever the grid's layout direction. The key already
     * means a side of the screen (GridNavigation moves toward the end on LEFT in an RTL grid), and translationX is
     * screen space too, so nothing is mirrored here. Allocation-free, unlike {@link #direction}.
     */
    static int dirX(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
    }

    /** Screen-space y of a D-pad key: DOWN is +1, UP is -1. */
    static int dirY(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
    }

    /** True when a key pressed at {@code keyAt} (uptime ms, 0 = never) is recent enough to explain a focus change at {@code now}. */
    static boolean recent(long now, long keyAt) {
        return keyAt > 0 && now >= keyAt && now - keyAt <= KEY_WINDOW_MS;
    }

    /** Where the artwork starts, as translationX in px, when focus arrives moving by {@code dx}: it lags, so against the move. */
    static float parallaxX(int dx, float parallaxDp, float density) {
        return -dx * parallaxDp * density;
    }

    /** The same for translationY, at {@link #VERTICAL_SHARE} of the distance. */
    static float parallaxY(int dy, float parallaxDp, float density) {
        return -dy * VERTICAL_SHARE * parallaxDp * density;
    }

    /** The D-pad direction of a key as {dx, dy}, each -1, 0 or 1: RIGHT is +1 x and DOWN is +1 y; x is mirrored for an RTL grid. */
    static int[] direction(int keyCode, boolean rtlGrid) {
        int dx = 0, dy = 0;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: dx = 1; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: dx = -1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: dy = 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: dy = -1; break;
            default: break;
        }
        return new int[]{rtlGrid ? -dx : dx, dy};
    }

    /**
     * The zoom that keeps a banner's edge out of sight while it is shifted {@code parallaxDp} either way:
     * 1 + (2P + 2) / width, never below 1.035. With P = 0 that is today's value on any tile 58 dp wide or more.
     */
    static float bannerScale(float parallaxDp, float tileWidthDp) {
        if (tileWidthDp <= 0) return BASE_BANNER_SCALE;
        return Math.max(BASE_BANNER_SCALE, 1f + (2f * parallaxDp + 2f) / tileWidthDp);
    }

    /** Where the sheen band starts, as translationX in px: off the card on the side focus came from. */
    static float sheenFrom(float cardWidthPx, int dx) {
        return (dx < 0 ? 0.8f : -0.8f) * cardWidthPx;
    }

    /** Where the sheen band ends: off the card on the other side. */
    static float sheenTo(float cardWidthPx, int dx) {
        return (dx < 0 ? -1.3f : 1.3f) * cardWidthPx;
    }

    /** Which way the shelf text moves on screen when focus goes from one position to another: +1 further along the row, mirrored for RTL, 0 when it did not move. */
    static int shelfDir(int previousPosition, int newPosition, boolean rtlGrid) {
        int d = Integer.compare(newPosition, previousPosition);
        return rtlGrid ? -d : d;
    }
}
