package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * The motion of one glass panel (HOME-DESIGN.md 19.5 and 19.7), reusable by every panel: the open spring, the exit fade, row
 * focus scale with its press dip, and the highlight parallax that moves the lens band and rim with the focused row. One
 * instance per panel; everything it animates is made once, here, so a focus move or a frame allocates nothing.
 *
 * Springs do not follow the system animator scale, so their stiffness is {@link GlassMotion#stiffness}; with animators off or
 * {@code animations} false everything snaps, and with {@code reduced_motion} only alpha moves ({@link GlassMotion#style}).
 */
final class GlassMotionController {
    private final View panel;
    private final GlassPanelDrawable drawable;   // null when the panel has no glass drawable
    private final float density;
    private final boolean animations, reduced;
    private final SpringAnimation scaleX, scaleY, lift, parallax;
    private final FloatValueHolder biasValue = new FloatValueHolder(0f);
    private final Rect tmp = new Rect();
    // The open and close numbers: a popup's by default, the full-screen sheet's after useSheetProfile().
    private float openScaleFrom = GlassMotion.OPEN_SCALE_FROM, openLiftDp = GlassMotion.OPEN_LIFT_DP;
    private float openStiffness = GlassMotion.OPEN_STIFFNESS, openDamping = GlassMotion.OPEN_DAMPING;
    private float closeScaleTo = GlassMotion.CLOSE_SCALE_TO;
    private int openAlphaMs = GlassMotion.OPEN_ALPHA_MS, closeMs = GlassMotion.CLOSE_MS;
    private int style = GlassMotion.STYLE_FULL;
    private float animatorScale = 1f;
    private float pivotFx = 0.5f, pivotFy = 0.5f;
    private boolean biasShown, chips, closing;
    private View focused;
    private Runnable pendingEnd;
    private final Runnable closeDone = () -> {
        Runnable end = pendingEnd;
        pendingEnd = null;
        if (end != null) end.run();
    };
    private final Runnable applyFocus = () -> {
        if (focused != null) moveHighlight(focused);
    };
    private final View.OnFocusChangeListener onFocus = (v, has) -> {
        if (has) {
            focused = v;
            scaleRow(v, false);
            moveHighlight(v);
        } else if (focused == v) {
            focused = null;
            scaleRow(v, false, false);
        }
    };
    private final View.OnKeyListener onKey = (v, keyCode, event) -> {
        if (focused != null && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) scaleRow(focused, true);
            else if (event.getAction() == KeyEvent.ACTION_UP) scaleRow(focused, false);
        }
        return false;   // never consumes: the row's own listener still gets the key
    };

    GlassMotionController(View panel, boolean animations, boolean reducedMotion) {
        this.panel = panel;
        this.animations = animations;
        this.reduced = reducedMotion;
        this.density = panel.getResources().getDisplayMetrics().density;
        android.graphics.drawable.Drawable bg = panel.getBackground();
        drawable = bg instanceof GlassPanelDrawable ? (GlassPanelDrawable) bg : null;
        scaleX = spring(new SpringAnimation(panel, DynamicAnimation.SCALE_X, 1f), 0.002f);
        scaleY = spring(new SpringAnimation(panel, DynamicAnimation.SCALE_Y, 1f), 0.002f);
        lift = spring(new SpringAnimation(panel, DynamicAnimation.TRANSLATION_Y, 0f), 0.5f);
        parallax = new SpringAnimation(biasValue);
        parallax.setSpring(new androidx.dynamicanimation.animation.SpringForce(0f));
        parallax.setMinimumVisibleChange(0.002f);
        parallax.addUpdateListener((a, value, velocity) -> {
            if (drawable != null) drawable.setHighlightOffset(value);
        });
    }

    /** The full-screen settings sheet (19.7): rises 24 dp and scales from 0.98 on a (500, 0.9) spring, alpha 160 ms; closes by alpha alone in 120 ms. */
    void useSheetProfile() {
        openScaleFrom = GlassMotion.SHEET_SCALE_FROM;
        openLiftDp = GlassMotion.SHEET_LIFT_DP;
        openStiffness = GlassMotion.SHEET_STIFFNESS;
        openDamping = GlassMotion.SHEET_DAMPING;
        openAlphaMs = GlassMotion.SHEET_ALPHA_MS;
        closeMs = GlassMotion.SHEET_CLOSE_MS;
        closeScaleTo = 1f;
    }

    private static SpringAnimation spring(SpringAnimation a, float minVisible) {
        a.setSpring(new androidx.dynamicanimation.animation.SpringForce(a.getSpring().getFinalPosition()));
        a.setMinimumVisibleChange(minVisible);
        return a;
    }

    /** The system animator duration scale; 1 when the firmware hides it. */
    private float readAnimatorScale() {
        try {
            return Settings.Global.getFloat(panel.getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (RuntimeException e) {
            return 1f;
        }
    }

    /** Re-reads the animator scale and the switches, and sets the springs' stiffness to match. Once per open and per close. */
    private void refresh() {
        animatorScale = readAnimatorScale();
        style = GlassMotion.style(ValueAnimator.areAnimatorsEnabled() && animatorScale > 0f, animations, reduced);
        setSpring(scaleX, openStiffness, openDamping);
        setSpring(scaleY, openStiffness, openDamping);
        setSpring(lift, openStiffness, openDamping);
        setSpring(parallax, GlassMotion.PARALLAX_STIFFNESS, GlassMotion.PARALLAX_DAMPING);
    }

    private void setSpring(SpringAnimation a, float base, float damping) {
        a.getSpring().setStiffness(GlassMotion.stiffness(base, animatorScale)).setDampingRatio(damping);
    }

    // ---- open and close ----

    /**
     * Call before the panel is shown: puts it at its starting pose, so the first frame is already right. {@code fx}, {@code fy}:
     * the pivot as a fraction of the panel (1, 0 is the top end, the corner nearest an anchor; 0.5, 0.5 the centre).
     */
    void prepareOpen(float fx, float fy) {
        refresh();
        pivotFx = fx;
        pivotFy = fy;
        closing = false;
        if (style == GlassMotion.STYLE_SNAP) {
            panel.setAlpha(1f);
            return;
        }
        panel.setAlpha(0f);
        if (style == GlassMotion.STYLE_FULL) {
            panel.setScaleX(openScaleFrom);
            panel.setScaleY(openScaleFrom);
            panel.setTranslationY(openLiftDp * density);
        }
    }

    /** Call right after the panel is shown: alpha 0 to 1 in 120 ms, and the springs, once the panel has a size to pivot about. */
    void runOpen() {
        if (style == GlassMotion.STYLE_SNAP) return;
        final boolean full = style == GlassMotion.STYLE_FULL;
        panel.animate().cancel();
        panel.animate().alpha(1f).setDuration(full ? openAlphaMs : Motion.REDUCED_MS)
                .setInterpolator(LINEAR).setListener(null).start();
        if (!full) return;
        if (panel.getWidth() > 0) {
            startSprings();
            return;
        }
        panel.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                panel.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!closing) startSprings();
                return true;
            }
        });
    }

    private void startSprings() {
        panel.setPivotX(panel.getWidth() * pivotFx);
        panel.setPivotY(panel.getHeight() * pivotFy);
        scaleX.start();
        scaleY.start();
        lift.start();
    }

    /** Back or focus lost: alpha to 0 (and scale to 0.96) in 110 ms {@code STANDARD}, then {@code end}. Snaps with animators off. */
    void close(Runnable end) {
        refresh();
        cancel();
        closing = true;
        if (style == GlassMotion.STYLE_SNAP) {
            closing = false;
            end.run();
            return;
        }
        pendingEnd = end;
        boolean full = style == GlassMotion.STYLE_FULL;
        panel.setPivotX(panel.getWidth() * pivotFx);
        panel.setPivotY(panel.getHeight() * pivotFy);
        android.view.ViewPropertyAnimator a = panel.animate().alpha(0f).setListener(null)
                .setDuration(full ? closeMs : Motion.REDUCED_MS).setInterpolator(FocusMotion.STANDARD);
        if (full) a.scaleX(closeScaleTo).scaleY(closeScaleTo);
        a.withEndAction(closeDone).start();
    }

    /** Stops everything in flight and drops a pending end action (a chosen row dismisses at once). */
    void cancel() {
        pendingEnd = null;
        closing = false;
        scaleX.cancel();
        scaleY.cancel();
        lift.cancel();
        parallax.cancel();
        panel.animate().cancel();
    }

    // ---- rows ----

    /**
     * Gives the focusable, clickable rows under {@code root} (or chips, scale 1.04) the focus scale and press dip, and makes the
     * panel's lens band and rim follow the focused one. Call once the rows are added; safe to call again. A focus listener a row
     * already has is replaced.
     */
    void watch(ViewGroup root, boolean chips) {
        this.chips = chips;
        panel.setOnKeyListener(onKey);
        walk(root);
    }

    private void walk(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable() && child.isClickable()) child.setOnFocusChangeListener(onFocus);
            else if (child instanceof ViewGroup) walk((ViewGroup) child);
        }
    }

    private void scaleRow(View v, boolean pressed) {
        scaleRow(v, true, pressed);
    }

    private void scaleRow(View v, boolean focus, boolean pressed) {
        if (style != GlassMotion.STYLE_FULL) return;
        float s = GlassMotion.rowScale(focus, chips, pressed);
        v.animate().scaleX(s).scaleY(s).setListener(null)
                .setDuration(pressed ? GlassMotion.ROW_PRESS_MS : GlassMotion.ROW_FOCUS_MS)
                .setInterpolator(FocusMotion.EASE_OUT).start();
    }

    private void moveHighlight(View row) {
        if (drawable == null || style == GlassMotion.STYLE_ALPHA) return;
        if (panel.getHeight() <= 0 || !(panel instanceof ViewGroup)) {
            panel.post(applyFocus);   // not laid out yet: the first focus lands before the first layout
            return;
        }
        tmp.set(0, 0, row.getWidth(), row.getHeight());
        ((ViewGroup) panel).offsetDescendantRectToMyCoords(row, tmp);
        float bias = GlassMotion.bias(tmp.exactCenterY(), panel.getHeight());
        if (style == GlassMotion.STYLE_SNAP || !biasShown) {
            biasShown = true;
            parallax.cancel();
            biasValue.setValue(bias);
            drawable.setHighlightOffset(bias);
        } else {
            parallax.animateToFinalPosition(bias);
        }
    }

    private static final LinearInterpolator LINEAR = new LinearInterpolator();
}
