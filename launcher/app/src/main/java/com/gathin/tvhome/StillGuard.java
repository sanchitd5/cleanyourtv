package com.gathin.tvhome;

import java.util.Random;

/**
 * Every schedule behind the stillness guard (HOME-DESIGN.md section 7): where the screensaver clock goes, how Home's
 * chrome drifts, when things dim and blank. Pure, so the guarantees are tested rather than hoped for.
 */
final class StillGuard {
    static final int CALM = 1, DIM = 2;
    static final int COLS = 4, ROWS = 3, CELLS = COLS * ROWS;
    /** The clock block never comes closer than this to the edge of the free range. */
    static final float MARGIN = 0.08f;
    static final long ORBIT_START_MS = 60_000L, ORBIT_EVERY_MS = 60_000L;

    private StillGuard() {}

    /**
     * Screensaver clock position for step n: {x, y} as fractions of the free range, each in [0.08, 0.92]. The range is
     * a 4x3 grid; each block of 12 steps visits every cell once, in an order shuffled by the seed and the block, and a
     * block never starts in the cell the one before ended in. The spot is jittered inside its cell by up to 35 % of the cell.
     */
    static float[] spot(int step, long seed) {
        int n = Math.max(0, step);
        int cycle = n / CELLS, i = n % CELLS;
        int[] perm = shuffled(seed + cycle);
        if (cycle > 0 && perm[0] == shuffled(seed + cycle - 1)[CELLS - 1]) {
            int t = perm[0];
            perm[0] = perm[1];
            perm[1] = t;
        }
        Random rng = new Random(seed * 31 + cycle + 1);   // jitter: its own generator, two draws per step of the block
        double jx = 0, jy = 0;
        for (int k = 0; k <= i; k++) {
            jx = rng.nextDouble() * 2 - 1;
            jy = rng.nextDouble() * 2 - 1;
        }
        float cellW = (1f - 2 * MARGIN) / COLS, cellH = (1f - 2 * MARGIN) / ROWS;
        int cell = perm[i];
        float x = MARGIN + (cell % COLS + 0.5f) * cellW + (float) jx * 0.35f * cellW;
        float y = MARGIN + (cell / COLS + 0.5f) * cellH + (float) jy * 0.35f * cellH;
        return new float[]{x, y};
    }

    /** The grid cell (0 to 11) step n lands in. */
    static int cell(int step, long seed) {
        float[] p = spot(step, seed);
        float cellW = (1f - 2 * MARGIN) / COLS, cellH = (1f - 2 * MARGIN) / ROWS;
        int col = Math.min(COLS - 1, (int) ((p[0] - MARGIN) / cellW));
        int row = Math.min(ROWS - 1, (int) ((p[1] - MARGIN) / cellH));
        return row * COLS + col;
    }

    private static int[] shuffled(long seed) {
        int[] perm = new int[CELLS];
        for (int i = 0; i < CELLS; i++) perm[i] = i;
        Random rng = new Random(seed);
        for (int i = CELLS - 1; i > 0; i--) {   // Fisher-Yates
            int j = rng.nextInt(i + 1);
            int t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        return perm;
    }

    /** Home chrome offset in dp for orbit step n: 8 points on an ellipse, 6 dp wide and 4 dp tall. */
    static float[] orbit(int step) {
        double a = 2 * Math.PI * Math.floorMod(step, 8) / 8;
        return new float[]{(float) (6 * Math.cos(a)), (float) (4 * Math.sin(a))};
    }

    /** Brightness factor for the screensaver after elapsedMs: 1 until afterMs, then a smoothstep down to floor over rampMs. afterMs <= 0 is off. */
    static float dim(long elapsedMs, long afterMs, long rampMs, float floor) {
        if (afterMs <= 0) return 1f;
        float t = rampMs <= 0 ? (elapsedMs >= afterMs ? 1f : 0f)
                : Math.max(0f, Math.min(1f, (float) (elapsedMs - afterMs) / rampMs));
        return 1f - (1f - floor) * t * t * (3f - 2f * t);
    }

    /** True once the screensaver has run afterMs; afterMs <= 0 never blanks. */
    static boolean blank(long elapsedMs, long afterMs) {
        return afterMs > 0 && elapsedMs >= afterMs;
    }

    /** Which Home idle stages apply after idleMs, as CALM and DIM bits; a stage with 0 ms is off. */
    static int homeStage(long idleMs, long calmMs, long dimMs) {
        int stage = 0;
        if (calmMs > 0 && idleMs >= calmMs) stage |= CALM;
        if (dimMs > 0 && idleMs >= dimMs) stage |= DIM;
        return stage;
    }

    /** The orbit step due after idleMs: -1 before 60 s, then one more every 60 s. */
    static int orbitStep(long idleMs) {
        return idleMs < ORBIT_START_MS ? -1 : (int) ((idleMs - ORBIT_START_MS) / ORBIT_EVERY_MS);
    }

    /** The screensaver caption sits bottom-start for even pictures, bottom-end for odd ones. */
    static boolean captionAtStart(int pictureIndex) {
        return pictureIndex % 2 == 0;
    }
}
