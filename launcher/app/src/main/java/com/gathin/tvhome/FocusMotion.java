package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** The numbers behind Horizon's focus motion (HOME-DESIGN.md section 4): pure functions and two shared interpolators. */
final class FocusMotion {
    /** Shared so a focus change allocates nothing. */
    static final Interpolator EASE_OUT = new DecelerateInterpolator(1.5f);
    static final Interpolator STANDARD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);

    /** The banner's zoom with no parallax: today's 1.035. */
    static final float BASE_BANNER_SCALE = 1.035f;

    private FocusMotion() {}

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
