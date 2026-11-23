package com.gathin.calibration;

/**
 * The level maths of the picture patterns, free of Android so the JVM can test it.
 *
 * A "code" is the 8-bit value written to the frame buffer (0..255). Limited range puts black at 16 and white
 * at 235; full range uses 0 and 255. The patterns draw codes as they are: the TV decides what they mean.
 */
final class Levels {
    private Levels() {}

    /** Near-black bars of the limited-range PLUGE, on a background of 16. */
    static final int[] PLUGE_LIMITED = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 25};
    /** Near-black bars of the full-range PLUGE, on a background of 0. */
    static final int[] PLUGE_FULL = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 14, 16, 20, 25};
    /** Gammas the checkerboard pattern compares. */
    static final double[] GAMMAS = {1.8, 2.0, 2.2, 2.4, 2.6};

    static int black(boolean limited) { return limited ? 16 : 0; }

    static int white(boolean limited) { return limited ? 235 : 255; }

    /** The code for {@code percent} of the way from black to white. */
    static int grey(double percent, boolean limited) {
        double f = Math.max(0, Math.min(100, percent)) / 100.0;
        return (int) Math.round(limited ? 16 + f * 219 : f * 255);
    }

    /** White clipping bars: every code from 235 to 255, whatever the range. */
    static int[] whiteClipCodes() {
        int[] codes = new int[21];
        for (int i = 0; i < codes.length; i++) codes[i] = 235 + i;
        return codes;
    }

    /** The solid code whose light equals half of the checkerboard's white on a display with this gamma. */
    static int gammaPatch(double gamma, boolean limited) {
        double f = Math.pow(0.5, 1.0 / gamma);
        return (int) Math.round(limited ? 16 + f * 219 : f * 255);
    }

    /** Bits of a colour mask: red 4, green 2, blue 1. */
    static final int R = 4, G = 2, B = 1;
    /** The seven bars of colour bars, left to right: white, yellow, cyan, green, magenta, red, blue. */
    static final int[] BAR_MASKS = {R | G | B, R | G, G | B, G, R | B, R, B};

    /** {r, g, b} of a mask at {@code amplitude} (0..1) of the black-to-white span. */
    static int[] bar(int mask, double amplitude, boolean limited) {
        int lo = black(limited);
        int hi = lo + (int) Math.round(amplitude * (white(limited) - lo));
        return new int[]{(mask & R) != 0 ? hi : lo, (mask & G) != 0 ? hi : lo, (mask & B) != 0 ? hi : lo};
    }

    /**
     * Step {@code step} of {@code steps} (0 .. steps-1) of a saturation sweep: from 50% grey to the full
     * colour of {@code mask}. Not constant luma; it is a way to see each primary or secondary grow.
     */
    static int[] saturation(int mask, int step, int steps, boolean limited) {
        double s = steps <= 1 ? 1 : (double) step / (steps - 1);
        int mid = grey(50, limited);
        int[] full = bar(mask, 1, limited);
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) out[i] = (int) Math.round(mid + s * (full[i] - mid));
        return out;
    }
}
