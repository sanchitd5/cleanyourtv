package com.gathin.tvhome;

/** The numbers of glass motion (HOME-DESIGN.md 19.5 and 19.7). Pure; the springs and animators that use them come in G4. */
final class GlassMotion {
    private GlassMotion() { }

    /** Panel opens: scale from, dp of lift, spring (stiffness, damping ratio) and the linear alpha fade in ms. */
    static final float OPEN_SCALE_FROM = 0.92f, OPEN_LIFT_DP = -6f, OPEN_STIFFNESS = 600f, OPEN_DAMPING = 0.80f;
    static final int OPEN_ALPHA_MS = 120;
    /** Panel dismissed by Back or lost focus: alpha to 0 and scale to this in this many ms. */
    static final float CLOSE_SCALE_TO = 0.96f;
    static final int CLOSE_MS = 110;
    /** Settings sheet: content lift dp, spring, alpha-in and alpha-out ms. */
    static final float SHEET_SCALE_FROM = 0.98f, SHEET_LIFT_DP = 24f, SHEET_STIFFNESS = 500f, SHEET_DAMPING = 0.9f;
    static final int SHEET_ALPHA_MS = 160, SHEET_CLOSE_MS = 120;
    /** Backdrop cross-fade on first arrival and on a resample, and the live resample period. */
    static final int FADE_FIRST_MS = 200, FADE_RESAMPLE_MS = 300, RESAMPLE_MS = 1000;
    /** Highlight parallax: spring, and the lens band's shift in dp and the rim's turn in degrees at bias 1. */
    static final float PARALLAX_STIFFNESS = 300f, PARALLAX_DAMPING = 0.9f, BAND_SHIFT_DP = 5f, RIM_TURN_DEGREES = 10f;
    /** Row focus: scale and duration. */
    static final float ROW_FOCUS_SCALE = 1.02f, CHIP_FOCUS_SCALE = 1.04f, ROW_PRESS_SCALE = 0.985f;
    static final int ROW_FOCUS_MS = 120, ROW_PRESS_MS = 80;
    /** The system animator duration scale is held to this range before it touches a spring. */
    static final float SCALE_MIN = 0.5f, SCALE_MAX = 3f;

    /**
     * Spring stiffness under the system animator duration scale: {@code base / (s * s)} with {@code s} held to 0.5 to 3, because
     * a spring's settling time goes with 1 / sqrt(stiffness). A scale that is not a number, or 0 or less, counts as 1: the
     * caller snaps instead when animators are off, and a firmware that hides the setting must not break a panel.
     */
    static float stiffness(float base, float scale) {
        float s = scale(scale);
        return base / (s * s);
    }

    /** The animator scale as it is used: 1 when unknown or not positive, else held to 0.5 to 3. */
    static float scale(float scale) {
        if (!(scale > 0f)) return 1f;
        return Math.max(SCALE_MIN, Math.min(SCALE_MAX, scale));
    }

    /** Where a row's centre sits in its panel, -1 at the top to 1 at the bottom (0 for an empty panel). */
    static float bias(float rowCentre, float panelHeight) {
        if (!(panelHeight > 0f)) return 0f;
        return Math.max(-1f, Math.min(1f, 2f * rowCentre / panelHeight - 1f));
    }

    /** How a panel moves: nothing (snap), alpha only (reduced_motion) or the full springs (19.7). */
    static final int STYLE_SNAP = 0, STYLE_ALPHA = 1, STYLE_FULL = 2;

    /**
     * Animators off (developer options scale 0) or the {@code animations} key off: snap. {@code reduced_motion}: alpha only at
     * {@link Motion#REDUCED_MS}, no scale, no parallax. Otherwise the full motion.
     */
    static int style(boolean animatorsEnabled, boolean animations, boolean reducedMotion) {
        if (!animatorsEnabled || !animations) return STYLE_SNAP;
        return reducedMotion ? STYLE_ALPHA : STYLE_FULL;
    }

    /** Cross-fade length: the first live sample replaces the still's copy in 200 ms, a later resample takes 300 ms. */
    static int fadeMs(boolean previousWasLive) {
        return previousWasLive ? FADE_RESAMPLE_MS : FADE_FIRST_MS;
    }

    /** The cross-fade fraction after {@code elapsedMs} of {@code durationMs}, linear, held to 0..1 (a zero duration is done). */
    static float fadeFraction(long elapsedMs, int durationMs) {
        if (durationMs <= 0) return 1f;
        return Math.max(0f, Math.min(1f, elapsedMs / (float) durationMs));
    }

    /** The new backdrop's paint alpha (0 to 255) at a cross-fade fraction; a NaN counts as finished. */
    static int fadeAlpha255(float fraction) {
        if (fraction != fraction) return 255;
        return Math.round(Math.max(0f, Math.min(1f, fraction)) * 255f);
    }

    /** The scale a row or chip rests at, is focused at, or is pressed to. */
    static float rowScale(boolean focused, boolean chip, boolean pressed) {
        if (!focused) return 1f;
        return pressed ? ROW_PRESS_SCALE : chip ? CHIP_FOCUS_SCALE : ROW_FOCUS_SCALE;
    }
}
