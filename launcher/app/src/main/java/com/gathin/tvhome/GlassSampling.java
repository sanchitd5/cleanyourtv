package com.gathin.tvhome;

/** The pure decisions behind the glass backdrop's samples (HOME-DESIGN.md 19.4): black frames, the 1 Hz throttle, which copy to draw. */
final class GlassSampling {
    private GlassSampling() { }

    /** At most one live sample a second. */
    static final long MIN_GAP_MS = 1000;
    /** This many failed or black samples in a row and live sampling is given up for the process. */
    static final int GIVE_UP_AFTER = 3;
    /** A frame whose every channel is at or under this counts as black (a failed readback is all zeros). */
    static final int BLACK_MAX = 2;

    static final int NONE = 0, STILL = 1, LIVE = 2;

    /** True when every pixel is (near) black. An empty frame counts as black. */
    static boolean isBlack(int[] px, int count) {
        for (int i = 0; i < count; i++) {
            int p = px[i];
            if (((p >> 16) & 0xFF) > BLACK_MAX || ((p >> 8) & 0xFF) > BLACK_MAX || (p & 0xFF) > BLACK_MAX) return false;
        }
        return true;
    }

    /** Whether a sample may be taken now; {@code last} below 0 means none was taken yet. */
    static boolean due(long now, long last, long minGapMs) {
        return last < 0 || now - last >= minGapMs;
    }

    static boolean giveUp(int consecutiveFailures) {
        return consecutiveFailures >= GIVE_UP_AFTER;
    }

    /** Which copy a panel draws: the live one when it is wanted, usable and present, else the still's, else none. */
    static int select(boolean liveEnabled, boolean liveAvailable, boolean hasLive, boolean hasStill) {
        if (liveEnabled && liveAvailable && hasLive) return LIVE;
        return hasStill ? STILL : NONE;
    }

    /**
     * The centred part of a srcW x srcH picture with the aspect aw:ah (what CENTER_CROP shows on a screen of that shape),
     * as {left, top, right, bottom} in source pixels.
     */
    static void centerCrop(int srcW, int srcH, int aw, int ah, int[] out) {
        if (srcW <= 0 || srcH <= 0 || aw <= 0 || ah <= 0) {
            out[0] = 0; out[1] = 0; out[2] = Math.max(srcW, 0); out[3] = Math.max(srcH, 0);
            return;
        }
        long wantW = (long) srcH * aw / ah;
        int cw = (int) Math.min(srcW, wantW);
        int ch = cw == srcW && wantW > srcW ? (int) ((long) srcW * ah / aw) : srcH;
        out[0] = (srcW - cw) / 2;
        out[1] = (srcH - ch) / 2;
        out[2] = out[0] + cw;
        out[3] = out[1] + ch;
    }
}
