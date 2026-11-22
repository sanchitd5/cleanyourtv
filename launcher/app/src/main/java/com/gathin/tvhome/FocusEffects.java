package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Horizon's per-tile focus effects (HOME-DESIGN.md 4.2 and 4.3): the artwork trails the D-pad move and springs back, and
 * one band of light crosses the tile. One instance per {@code AppAdapter.Holder}, made only when the theme asks for an
 * effect and animations are on without reduced_motion.
 *
 * <p>Nothing here runs from onDraw, onScrolled or a frame callback. After a holder's first use a focus change allocates
 * nothing: the springs, the sheen animator, its listeners and the shared drawable are made once and reused, and the
 * start values are written into fields. The springs ignore the system animator scale (DynamicAnimation has no notion
 * of it), so {@link ValueAnimator#areAnimatorsEnabled()} gates every effect: with animations switched off in developer
 * options the artwork and the band simply stay still. The sheen is a ValueAnimator and follows a scale below 1.
 */
final class FocusEffects {
    private static final float STIFFNESS = 320f;
    private static final float DAMPING = 0.62f;
    private static final long SHEEN_MS = 520;
    private static final long SHEEN_DELAY_MS = 90;

    // The last D-pad key the card key listener saw. UI thread only.
    private static int lastKey;
    private static long lastKeyAt;
    private static Drawable.ConstantState sheenState;

    /** Called from the card's key listener for every D-pad key down, before focus moves. */
    static void noteKey(int keyCode) {
        lastKey = keyCode;
        lastKeyAt = SystemClock.uptimeMillis();
    }

    /** A fresh sheen background from one shared GradientDrawable: LEFT_RIGHT, clear, 22 % white, clear. */
    static Drawable newSheen() {
        if (sheenState == null) {
            sheenState = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF}).getConstantState();
        }
        return sheenState.newDrawable();
    }

    /** Null when nothing is asked for, so a default theme pays nothing. {@code card} is the tile's {@code @id/card}. */
    static FocusEffects create(View root, boolean animations, boolean reducedMotion, int parallaxDp, boolean sheen,
                               boolean advancedEffects, int cardW, int cardH, float density) {
        View sheenView = root.findViewById(R.id.sheen);
        if (!animations || reducedMotion || (parallaxDp <= 0 && !sheen)) {
            if (sheenView != null) sheenView.setVisibility(View.GONE);
            return null;
        }
        float scale = parallaxDp > 0 ? FocusMotion.bannerScale(parallaxDp, cardW / density)
                : advancedEffects ? FocusMotion.BASE_BANNER_SCALE : 1f;
        FocusEffects fx = new FocusEffects(root.findViewById(R.id.banner), root.findViewById(R.id.icon),
                sheen ? sheenView : null, parallaxDp, density, scale, cardW);
        if (sheen) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cardW / 2, Math.round(cardH * 1.8f),
                    Gravity.LEFT | Gravity.CENTER_VERTICAL);
            sheenView.setLayoutParams(lp);
            sheenView.setBackground(newSheen());
            sheenView.setRotation(20f);
        } else if (sheenView != null) {
            sheenView.setVisibility(View.GONE);
        }
        return fx;
    }

    private final View banner, icon, sheen;
    private final float parallaxDp, density, cardW;
    /** The banner's zoom while focused: room for the parallax, or today's value. */
    final float bannerScale;
    // banner x, banner y, icon x, icon y; made on first use.
    private final SpringAnimation[] springs = new SpringAnimation[4];
    private ValueAnimator sheenAnim;
    private float sheenFrom, sheenTo;

    private FocusEffects(View banner, View icon, View sheen, int parallaxDp, float density, float bannerScale, int cardW) {
        this.banner = banner;
        this.icon = icon;
        this.sheen = sheen;
        this.parallaxDp = parallaxDp;
        this.density = density;
        this.bannerScale = bannerScale;
        this.cardW = cardW;
    }

    /** A bind (new or recycled holder, or a rebind of the focused tile): the resting state, with nothing animating. */
    void onBind(boolean focused) {
        stop();
        if (banner.getTranslationX() != 0f) banner.setTranslationX(0f);
        if (banner.getTranslationY() != 0f) banner.setTranslationY(0f);
        if (icon.getTranslationX() != 0f) icon.setTranslationX(0f);
        if (icon.getTranslationY() != 0f) icon.setTranslationY(0f);
        if (parallaxDp > 0) {
            float s = focused ? bannerScale : 1f;
            banner.setScaleX(s);
            banner.setScaleY(s);
        }
    }

    /**
     * Focus changed on the tile. Runs after the adapter has cancelled the banner's animator. Gains read the last
     * D-pad key (none, when focus did not come from one: arrival, a rebuild); losses animate the artwork home inside
     * the tile's own 170 ms scale-down.
     */
    void onFocus(boolean focused, boolean hasBanner) {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            onBind(focused);
            return;
        }
        if (!focused) {
            banner.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                    .setDuration(170).setInterpolator(FocusMotion.EASE_OUT).start();
            if (icon.getTranslationX() != 0f) icon.setTranslationX(0f);
            if (icon.getTranslationY() != 0f) icon.setTranslationY(0f);
            return;
        }
        banner.animate().scaleX(bannerScale).scaleY(bannerScale).setDuration(280).start();
        boolean keyed = FocusMotion.recent(SystemClock.uptimeMillis(), lastKeyAt);
        int dx = keyed ? FocusMotion.dirX(lastKey) : 0;
        int dy = keyed ? FocusMotion.dirY(lastKey) : 0;
        if (parallaxDp > 0 && (dx | dy) != 0) {
            float k = hasBanner ? 1f : FocusMotion.ICON_SHARE;
            View art = hasBanner ? banner : icon;
            int base = hasBanner ? 0 : 2;
            art.setTranslationX(FocusMotion.parallaxX(dx, parallaxDp, density) * k);
            art.setTranslationY(FocusMotion.parallaxY(dy, parallaxDp, density) * k);
            if (dx != 0) spring(base, art, DynamicAnimation.TRANSLATION_X).start();
            if (dy != 0) spring(base + 1, art, DynamicAnimation.TRANSLATION_Y).start();
        }
        if (sheen != null && keyed) startSheen(dx);
    }

    private SpringAnimation spring(int i, View v, DynamicAnimation.ViewProperty property) {
        SpringAnimation s = springs[i];
        if (s == null) {
            s = new SpringAnimation(v, property, 0f);
            s.setSpring(new SpringForce(0f).setStiffness(STIFFNESS).setDampingRatio(DAMPING));
            springs[i] = s;
        }
        return s;
    }

    private void startSheen(int dx) {
        if (sheenAnim == null) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(SHEEN_MS);
            a.setStartDelay(SHEEN_DELAY_MS);
            a.setInterpolator(FocusMotion.STANDARD);
            a.addUpdateListener(v -> sheen.setTranslationX(sheenFrom + (sheenTo - sheenFrom) * v.getAnimatedFraction()));
            a.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    sheen.setVisibility(View.INVISIBLE);
                }
            });
            sheenAnim = a;
        }
        sheenFrom = FocusMotion.sheenFrom(cardW, dx);
        sheenTo = FocusMotion.sheenTo(cardW, dx);
        sheen.setTranslationX(sheenFrom);
        sheen.setVisibility(View.VISIBLE);
        sheenAnim.start();
    }

    /** Cancels the springs and the band; the band ends invisible. */
    private void stop() {
        for (SpringAnimation s : springs) if (s != null && s.isRunning()) s.cancel();
        if (sheenAnim != null && sheenAnim.isStarted()) sheenAnim.cancel();
        else if (sheen != null && sheen.getVisibility() != View.INVISIBLE) sheen.setVisibility(View.INVISIBLE);
    }
}
