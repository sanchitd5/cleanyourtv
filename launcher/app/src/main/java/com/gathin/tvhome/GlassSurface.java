package com.gathin.tvhome;

/**
 * The pure geometry and selection rules of a glass panel (HOME-DESIGN.md 19.3 and 19.5): which numbers {@link GlassPanelDrawable}
 * draws with. Kept free of android.graphics so the unit tests can run it.
 */
final class GlassSurface {
    private GlassSurface() { }

    /** Panel radii in dp (19.5): popups and the quick panel, settings groups, the app library, dialogs. */
    static final float POPUP_RADIUS_DP = 27f, SETTINGS_RADIUS_DP = 24f, LIBRARY_RADIUS_DP = 32f, DIALOG_RADIUS_DP = 27f;
    /** Padding between a panel and its rows, and the rows' radius (a full capsule at 42 dp). */
    static final float POPUP_PAD_DP = 6f, ROW_RADIUS_DP = 21f;
    /** The rim and the inner shadow are 1 dp lines; the shadow sits 1 dp in from the edge. */
    static final float LINE_DP = 1f, SHADOW_INSET_DP = 1f;
    /** The rim gradient's angle in the CSS sense: 135 degrees runs from the top-left corner to the bottom-right. */
    static final float RIM_ANGLE = 135f;

    /** Concentric corners: the inner radius is the outer radius minus the padding between them, never negative. */
    static float concentric(float outer, float padding) {
        return Math.max(0f, outer - padding);
    }

    /** A radius no bigger than half the shorter side, so a small panel is a capsule rather than a malformed path. */
    static float fitRadius(float radius, float w, float h) {
        return Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
    }

    /**
     * The end points of a linear gradient of CSS {@code angleDegrees} across a w by h box, whose line passes the centre and is as
     * long as the box's projection onto it (so the corners take exactly the first and last colours):
     * {@code out = {x0, y0, x1, y1}}.
     */
    static void gradientLine(float w, float h, float angleDegrees, float[] out) {
        double a = Math.toRadians(angleDegrees);
        double dx = Math.sin(a), dy = -Math.cos(a);
        double half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2.0;
        float cx = w / 2f, cy = h / 2f;
        out[0] = (float) (cx - dx * half);
        out[1] = (float) (cy - dy * half);
        out[2] = (float) (cx + dx * half);
        out[3] = (float) (cy + dy * half);
    }

    /** Height of the top lens band and where the bottom one starts, for a panel h high. */
    static float bandTopEnd(float h) {
        return GlassTint.BAND_TOP_END * h;
    }

    static float bandBottomStart(float h) {
        return GlassTint.BAND_BOTTOM_START * h;
    }

    /** Tint alpha (0 to 1) of a material: adaptive for Glass, fixed for Tinted and Solid. */
    static float tintAlpha(int mode, float peak) {
        if (mode == GlassTint.MODE_GLASS) return GlassTint.tintAlpha(peak);
        return mode == GlassTint.MODE_TINTED ? GlassTint.TINTED_ALPHA : GlassTint.SOLID_ALPHA;
    }

    /** The tint colour (opaque RGB) of a material. */
    static int tintColor(int mode) {
        if (mode == GlassTint.MODE_GLASS) return GlassTint.GLASS_TINT;
        return mode == GlassTint.MODE_TINTED ? GlassTint.TINTED_TINT : GlassTint.SOLID_TINT;
    }

    /** Strength of the top lens band: adaptive for Glass, fixed for Tinted (0.08) and Solid (0.05). */
    static float bandAlpha(int mode, float peak) {
        if (mode == GlassTint.MODE_GLASS) return GlassTint.bandAlpha(peak);
        return mode == GlassTint.MODE_TINTED ? GlassTint.TINTED_BAND : GlassTint.SOLID_BAND;
    }

    /** Strength of the bottom band: a third of the top one. */
    static float bottomBandAlpha(int mode, float peak) {
        return bandAlpha(mode, peak) * GlassTint.BAND_BOTTOM_RATIO;
    }

    /** Only Glass shows the backdrop, and only when there is one and the panel's place on screen is known. */
    static boolean drawsBackdrop(int mode, boolean haveBackdrop, boolean positioned) {
        return mode == GlassTint.MODE_GLASS && haveBackdrop && positioned;
    }

    /** Solid has a flat rim; the other two a gradient. */
    static boolean flatRim(int mode) {
        return mode == GlassTint.MODE_SOLID;
    }

    /** {@code alpha} (0 to 1) as 0 to 255, rounded and clamped. */
    static int alpha255(float alpha) {
        return Math.max(0, Math.min(255, Math.round(alpha * 255f)));
    }

    /** {@code rgb} with the alpha channel replaced by {@code alpha} (0 to 1). */
    static int withAlpha(int rgb, float alpha) {
        return (alpha255(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    /** Highlight bias held to -1..1 (a NaN counts as 0). */
    static float clampBias(float bias) {
        if (bias != bias) return 0f;
        return Math.max(-1f, Math.min(1f, bias));
    }

    /** The lens band's shift in px for a bias (19.5: bias * 5 dp). */
    static float bandShiftPx(float bias, float density) {
        return clampBias(bias) * GlassMotion.BAND_SHIFT_DP * density;
    }

    /** The rim gradient's turn in degrees for a bias (19.5: bias * 10). */
    static float rimTurnDegrees(float bias) {
        return clampBias(bias) * GlassMotion.RIM_TURN_DEGREES;
    }
}
