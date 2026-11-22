package com.gathin.tvhome;

import android.graphics.Outline;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * The seam that turns the launcher's own panels into glass (HOME-DESIGN.md 19.11): {@link #apply} puts a
 * {@link GlassPanelDrawable} behind a panel, {@link #rows} restyles the rows inside it at run time (so no layout is forked).
 * Both do nothing while {@code glass_surfaces} is off, so a theme without glass keeps today's look exactly.
 */
final class Glass {
    private Glass() { }

    private static volatile boolean glassOn, reduceTransparency, animations = true, reducedMotion;

    /** Reads the two settings that matter here. Called once Home has its {@link Config}; every activity of the process may call it. */
    static void configure(Config cfg) {
        glassOn = cfg.glassSurfaces;
        reduceTransparency = cfg.glassReduceTransparency;
        animations = cfg.animations;
        reducedMotion = cfg.reducedMotion;
    }

    /** The theme's {@code animations} key. */
    static boolean animations() {
        return animations;
    }

    /** The theme's {@code reduced_motion} key. */
    static boolean reducedMotion() {
        return reducedMotion;
    }

    static boolean on() {
        return glassOn;
    }

    /**
     * Makes {@code panel} a glass panel of {@code radiusDp} corners: the drawable as background, a rounded outline (alpha 1, so
     * the elevation shadow falls around the panel and not under its translucent body) and a clip to it. {@code homeHosted}: the
     * panel lives in Home's window (a popup or a dialog over Home), so it is Glass whether or not Home has reported itself visible.
     * Returns whether glass was applied.
     */
    static boolean apply(View panel, float radiusDp, boolean homeHosted) {
        if (!glassOn) return false;
        final float density = panel.getResources().getDisplayMetrics().density;
        final float radius = radiusDp * density;
        final GlassPanelDrawable drawable = new GlassPanelDrawable(radius, density);
        drawable.setPolicy(true, reduceTransparency, homeHosted);
        drawable.setFadeAllowed(animations && !reducedMotion);
        panel.setBackground(drawable);
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                outline.setAlpha(1f);
            }
        });
        panel.setClipToOutline(true);
        panel.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> locate(v, drawable));
        panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.post(() -> locate(v, drawable));
            }

            @Override
            public void onViewDetachedFromWindow(View v) { }
        });
        return true;
    }

    private static void locate(View v, GlassPanelDrawable drawable) {
        if (!v.isAttachedToWindow()) return;
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        DisplayMetrics dm = v.getResources().getDisplayMetrics();
        drawable.setScreenPosition(at[0], at[1], dm.widthPixels, dm.heightPixels);
    }

    /**
     * Restyles the focusable rows and buttons under {@code root} (19.5): a transparent row that becomes a white capsule with dark
     * ink when focused, primary text and icons in ink colours, secondary text at 78 % and the status line at 12 sp. Call it once
     * the rows are added. Safe to call again.
     */
    static void rows(ViewGroup root) {
        rows(root, R.drawable.glass_row_bg);
    }

    /** As {@link #rows} for the settings sheet and its dialogs: rows keep a chip fill at rest and show the selected choice with the ice rim (19.5). */
    static void chips(ViewGroup root) {
        rows(root, R.drawable.glass_chip_bg);
    }

    private static void rows(ViewGroup root, int background) {
        if (!glassOn) return;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.isFocusable() && child.isClickable()) {
                child.setBackgroundResource(background);
                ink(child);
            } else if (child instanceof ViewGroup) {
                rows((ViewGroup) child, background);
            }
        }
    }

    private static void ink(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            boolean secondary = isSecondary(t.getId());
            t.setTextColor(t.getResources().getColorStateList(secondary ? R.color.glass_ink_mid : R.color.glass_ink, null));
            if (t.getId() == R.id.status) t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        } else if (v instanceof ImageView) {
            ((ImageView) v).setImageTintList(v.getResources().getColorStateList(R.color.glass_ink, null));
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) ink(g.getChildAt(i));
        }
    }

    /** Secondary lines: an input's status, a card's app, package and key. */
    static boolean isSecondary(int id) {
        return id == R.id.status || id == R.id.app || id == R.id.pkg || id == R.id.digest;
    }
}
