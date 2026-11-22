package com.gathin.tvhome;

/**
 * Top bar geometry (HOME-DESIGN.md section 21): pure arithmetic, no Android imports, so the numbers are unit tested.
 * Pixel values are at 1920x1080 and density 2.0; the overloads taking a density scale the dp-derived gaps.
 */
final class TopBarLayout {
    private TopBarLayout() {}

    static final int DRIFT_X_DP = 6, DRIFT_Y_DP = 4;
    /** Left and right margin: the dock band's outer edge. */
    static final int MARGIN_DP = 57;
    /** Bar centre line C. */
    static final int SPLIT_CENTRE_DP = 62, TVOS_CENTRE_DP = 54;
    /** Widest the corner scrim is stretched. */
    static final float MAX_SCALE_X = 1.25f;

    /** Longest the bar stays hidden waiting for its text widths and the capsule to settle (ms). */
    static final int SETTLE_TIMEOUT_MS = 500;
    /** Reveal fade (ms). */
    static final int REVEAL_MS = 150;

    /** Whether the text widths (clock, AM/PM, date) are unchanged across two consecutive passes, or the timeout has elapsed. prev is null before the first pass. */
    static boolean stable(int[] prev, int[] cur, long elapsedMs) {
        if (elapsedMs >= SETTLE_TIMEOUT_MS) return true;
        return prev != null && cur != null && java.util.Arrays.equals(prev, cur);
    }

    /** The tvos capsule's width the layout must reach for the time to sit inside it: its box from {@link #capsule}. */
    static int capsuleWidth(int rightEdge, int[] buttonWidths, int clockW, int ampmW, float density) {
        Capsule c = capsule(rightEdge, 0, buttonWidths, clockW, ampmW, density);
        return c.right - c.left;
    }

    /** The layout actually used: the configured one, or classic for right-to-left menus and the extra date lines. */
    static String mode(String value, boolean rtl, boolean shamsi, boolean lunar) {
        return rtl || shamsi || lunar ? Config.TOP_BAR_MODE_CLASSIC : Config.topBarLayout(value);
    }

    static int marginX(float density) { return Math.round(MARGIN_DP * density); }

    /** Bar centre line in px, or 0 for classic (which places nothing). */
    static int centreY(String mode, float density) {
        if (Config.TOP_BAR_MODE_SPLIT.equals(mode)) return Math.round(SPLIT_CENTRE_DP * density);
        if (Config.TOP_BAR_MODE_TVOS.equals(mode)) return Math.round(TVOS_CENTRE_DP * density);
        return 0;
    }

    /** Baseline of text whose cap height is centred on centre. */
    static int baselineY(int centre, int capHeight) { return centre + capHeight / 2; }

    /** Baseline of the greeting slot: 100 dp in split; under the date in tvos (79 dp), or 59 dp (the date's own line) when the date is off. */
    static int greetingBaselineY(String mode, boolean dateOn, float density) {
        if (Config.TOP_BAR_MODE_SPLIT.equals(mode)) return Math.round(100 * density);
        return Math.round((dateOn ? 79 : 59) * density);
    }

    /** Where the pieces of the split row start (ink x), and where the row ends. ampmX and sepX use the closed-up row when there is no AM/PM. */
    static final class SplitRow {
        final int ampmX, sepX, dateX, end;
        SplitRow(int ampmX, int sepX, int dateX, int end) { this.ampmX = ampmX; this.sepX = sepX; this.dateX = dateX; this.end = end; }
    }

    /** Clock, 16 px, AM/PM, 24 px, separator 2 px, 24 px, date; ampmW 0 closes the AM/PM and its gap up. */
    static SplitRow splitRow(int inkLeft, int clockW, int ampmW, int dateW) { return splitRow(inkLeft, clockW, ampmW, dateW, 2f); }

    static SplitRow splitRow(int inkLeft, int clockW, int ampmW, int dateW, float density) {
        int gapAmpm = Math.round(8 * density), gap = Math.round(12 * density), sepW = Math.max(1, Math.round(density));   // 1 dp
        int clockEnd = inkLeft + clockW;
        int ampmX = clockEnd + gapAmpm;
        int afterAmpm = ampmW > 0 ? ampmX + ampmW : clockEnd;
        int sepX = afterAmpm + gap;
        int dateX = sepX + sepW + gap;
        return new SplitRow(ampmW > 0 ? ampmX : clockEnd, sepX, dateX, dateX + dateW);
    }

    /** The tvos capsule: its box and where the clock ink and AM/PM start. */
    static final class Capsule {
        final int left, top, right, bottom, clockX, ampmX;
        Capsule(int left, int top, int right, int bottom, int clockX, int ampmX) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom; this.clockX = clockX; this.ampmX = ampmX;
        }
    }

    static Capsule capsule(int rightEdge, int centre, int[] buttonWidths, int clockW, int ampmW) {
        return capsule(rightEdge, centre, buttonWidths, clockW, ampmW, 2f);
    }

    /** 4 dp padding and gaps, 20 px before the clock ink, 8 px before AM/PM, 16 dp end padding, 44 dp tall. */
    static Capsule capsule(int rightEdge, int centre, int[] buttonWidths, int clockW, int ampmW, float density) {
        int pad = Math.round(4 * density), gap = pad, beforeClock = Math.round(10 * density),
                beforeAmpm = Math.round(4 * density), end = Math.round(16 * density), h = Math.round(44 * density);
        int w = pad;
        for (int i = 0; i < buttonWidths.length; i++) w += buttonWidths[i] + (i < buttonWidths.length - 1 ? gap : 0);
        w += beforeClock;
        int clockOff = w;
        w += clockW;
        int ampmOff = w + beforeAmpm;
        if (ampmW > 0) w += beforeAmpm + ampmW;
        w += end;
        int left = rightEdge - w, top = centre - h / 2;
        return new Capsule(left, top, rightEdge, top + h, left + clockOff, left + ampmOff);
    }

    // The 4.5:1 zone of ShelfScrim's corner, an ellipse from the top-left corner (section 21.6).

    /** Right limit of the zone at height y with the corner stretched by s; 0 when y is below the zone. */
    static int zoneRight(float y, float s) {
        double t = y / ShelfScrim.ZONE_H;
        return t >= 1 ? 0 : (int) Math.round(ShelfScrim.ZONE_W * s * Math.sqrt(1 - t * t));
    }

    /** The smallest stretch, 1 to 1.25, that puts every (x, y) pair (drift included) inside the zone; pairs that cannot fit give 1.25. */
    static float cornerScaleX(float... xy) {
        float s = 1f;
        for (int i = 0; i + 1 < xy.length; i += 2) {
            double t = xy[i + 1] / ShelfScrim.ZONE_H;
            if (t >= 1) return MAX_SCALE_X;
            s = Math.max(s, (float) (xy[i] / (ShelfScrim.ZONE_W * Math.sqrt(1 - t * t))));
        }
        return Math.min(MAX_SCALE_X, s);
    }

    /** Widest a line starting at dateLeft with baseline y may be, drift (12 x 8 px at density 2) included; 0 if none. */
    static int dateMaxWidth(int dateLeft, float y, float s, float density) {
        int right = zoneRight(y + DRIFT_Y_DP * density, s) - Math.round(DRIFT_X_DP * density);
        return Math.max(0, right - dateLeft);
    }
}
