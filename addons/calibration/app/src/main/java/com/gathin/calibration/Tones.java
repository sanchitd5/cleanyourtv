package com.gathin.calibration;

import java.util.Random;

/** The sound test signals, generated as 16-bit PCM and free of Android so the JVM can test them. */
final class Tones {
    private Tones() {}

    static final int RATE = 48000;
    /** The loudest any signal's peak may be, in dB below full scale. */
    static final double MAX_DBFS = -12;
    static final double RAMP_SECONDS = 0.05;
    /** The EQ bands of the TV, in Hz. */
    static final int[] BANDS = {125, 250, 500, 1000, 2000, 4000, 8000};
    static final double TONE_SECONDS = 4, GAP_SECONDS = 1;
    /** Left-right test: one cycle is left, gap, right, gap, each a second. */
    static final double LR_CYCLE_SECONDS = 4;
    static final int LR_CYCLES = 3;

    /** Peak amplitude (0..1) for {@code dbfs}, never above {@link #MAX_DBFS}. */
    static double amplitude(double dbfs) {
        return Math.pow(10, Math.min(dbfs, MAX_DBFS) / 20);
    }

    static short[] silence(double seconds) {
        return new short[(int) Math.round(seconds * RATE)];
    }

    /** A sine of {@code hz} for {@code seconds}, peaking at -12 dBFS, ramped in and out over 50 ms. */
    static short[] sine(double hz, double seconds) {
        int n = (int) Math.round(seconds * RATE);
        double a = amplitude(MAX_DBFS);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = a * Math.sin(2 * Math.PI * hz * i / RATE);
        return ramp(x);
    }

    /** Pink noise (about -3 dB per octave), scaled so its peak is -12 dBFS, ramped; the same for a seed. */
    static short[] pinkNoise(double seconds, long seed) {
        int n = (int) Math.round(seconds * RATE);
        Random rnd = new Random(seed);
        double b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
        double[] x = new double[n];
        double peak = 1e-9;
        for (int i = 0; i < n; i++) {
            double w = rnd.nextDouble() * 2 - 1;
            // Paul Kellet's economical pink filter.
            b0 = 0.99886 * b0 + w * 0.0555179;
            b1 = 0.99332 * b1 + w * 0.0750759;
            b2 = 0.96900 * b2 + w * 0.1538520;
            b3 = 0.86650 * b3 + w * 0.3104856;
            b4 = 0.55000 * b4 + w * 0.5329522;
            b5 = -0.7616 * b5 - w * 0.0168980;
            x[i] = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362;
            b6 = w * 0.115926;
            peak = Math.max(peak, Math.abs(x[i]));
        }
        double scale = amplitude(MAX_DBFS) / peak;
        for (int i = 0; i < n; i++) x[i] *= scale;
        return ramp(x);
    }

    /** A logarithmic sweep from {@code f0} to {@code f1} Hz over {@code seconds}, -12 dBFS peak, ramped. */
    static short[] sweep(double f0, double f1, double seconds) {
        int n = (int) Math.round(seconds * RATE);
        double a = amplitude(MAX_DBFS);
        double k = Math.log(f1 / f0);
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double phase = 2 * Math.PI * f0 * seconds / k * (Math.exp(t * k / seconds) - 1);
            x[i] = a * Math.sin(phase);
        }
        return ramp(x);
    }

    /** The band sequence: each band's sine for 4 s, then 1 s of silence. */
    static short[] bandSequence() {
        short[][] parts = new short[BANDS.length * 2][];
        for (int i = 0; i < BANDS.length; i++) {
            parts[2 * i] = sine(BANDS[i], TONE_SECONDS);
            parts[2 * i + 1] = silence(GAP_SECONDS);
        }
        return concat(parts);
    }

    /** Index into {@link #BANDS} playing at {@code t} seconds, or -1 in a gap or past the end. */
    static int bandAt(double t) {
        double slot = TONE_SECONDS + GAP_SECONDS;
        if (t < 0 || t >= slot * BANDS.length) return -1;
        return (t % slot) < TONE_SECONDS ? (int) (t / slot) : -1;
    }

    static double bandSequenceSeconds() { return (TONE_SECONDS + GAP_SECONDS) * BANDS.length; }

    /** Interleaved stereo for the left-right test: 1 kHz for a second on the left, then on the right. */
    static short[] leftRight() {
        short[] tone = sine(1000, 1);
        short[] gap = silence(1);
        int per = tone.length + gap.length;
        short[] out = new short[(int) (LR_CYCLES * LR_CYCLE_SECONDS * RATE) * 2];
        for (int c = 0; c < LR_CYCLES; c++) {
            int base = c * per * 2;
            for (int i = 0; i < tone.length; i++) {
                out[(base + i) * 2] = tone[i];                       // left, second 0..1
                out[(base + per + i) * 2 + 1] = tone[i];             // right, second 2..3
            }
        }
        return out;
    }

    /** -1 silent, 0 left, 1 right at {@code t} seconds of the left-right test. */
    static int sideAt(double t) {
        if (t < 0 || t >= LR_CYCLE_SECONDS * LR_CYCLES) return -1;
        double p = t % LR_CYCLE_SECONDS;
        return p < 1 ? 0 : (p >= 2 && p < 3 ? 1 : -1);
    }

    static short[] concat(short[]... parts) {
        int n = 0;
        for (short[] p : parts) n += p.length;
        short[] out = new short[n];
        int at = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    /** Applies a raised-cosine 50 ms fade in and out, and rounds to 16 bits. */
    private static short[] ramp(double[] x) {
        int n = x.length;
        int r = Math.min((int) Math.round(RAMP_SECONDS * RATE), n / 2);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double g = 1;
            if (i < r) g = 0.5 - 0.5 * Math.cos(Math.PI * i / r);
            else if (i >= n - r) g = 0.5 - 0.5 * Math.cos(Math.PI * (n - 1 - i) / r);
            out[i] = (short) Math.round(x[i] * g * 32767);
        }
        return out;
    }
}
