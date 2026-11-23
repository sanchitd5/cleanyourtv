package com.gathin.calibration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TonesTest {
    private static final double LIMIT = Math.pow(10, -12 / 20.0) * 32767;

    private static int peak(short[] x) {
        int p = 0;
        for (short s : x) p = Math.max(p, Math.abs(s));
        return p;
    }

    /** Upward zero crossings per second between samples [from, to). */
    private static double frequency(short[] x, int from, int to) {
        int crossings = 0;
        for (int i = from + 1; i < to; i++) if (x[i - 1] < 0 && x[i] >= 0) crossings++;
        return crossings * (double) Tones.RATE / (to - from);
    }

    @Test public void amplitudeIsCappedAtMinus12() {
        assertEquals(0.2512, Tones.amplitude(-12), 0.0001);
        assertEquals(0.2512, Tones.amplitude(0), 0.0001);   // asked louder: still -12
        assertEquals(0.1, Tones.amplitude(-20), 0.0001);
    }

    @Test public void sineHasTheRightFrequencyAndPeak() {
        for (int hz : Tones.BANDS) {
            short[] s = Tones.sine(hz, 1);
            assertEquals(Tones.RATE, s.length);
            assertEquals(hz, frequency(s, Tones.RATE / 4, Tones.RATE * 3 / 4), hz * 0.01 + 1);
            // Samples may miss the crest of a high tone by a cosine of up to 30 degrees.
            assertTrue(peak(s) >= LIMIT * 0.85 && peak(s) <= Math.ceil(LIMIT));
        }
    }

    @Test public void sineRampsInAndOut() {
        short[] s = Tones.sine(1000, 1);
        int ramp = (int) (Tones.RAMP_SECONDS * Tones.RATE);
        assertEquals(0, s[0]);
        assertEquals(0, s[s.length - 1]);
        // Within the ramp the envelope is below the full level; after it, full.
        int early = 0, late = 0;
        for (int i = 0; i < ramp / 4; i++) early = Math.max(early, Math.abs(s[i]));
        for (int i = ramp * 2; i < ramp * 3; i++) late = Math.max(late, Math.abs(s[i]));
        assertTrue(early < LIMIT * 0.2);
        assertTrue(late > LIMIT * 0.95);
    }

    @Test public void pinkNoiseIsSeededSafeAndTiltedDown() {
        short[] a = Tones.pinkNoise(2, 7), b = Tones.pinkNoise(2, 7);
        assertArrayEquals(a, b);
        assertTrue(peak(a) <= Math.ceil(LIMIT));
        assertEquals(0, a[0]);
        // Pink noise has far more energy below 1 kHz than above 4 kHz: a moving average keeps the lows.
        double low = 0, total = 0;
        for (int i = 8; i < a.length; i++) {
            double smooth = 0;
            for (int k = 0; k < 8; k++) smooth += a[i - k];
            smooth /= 8;
            low += smooth * smooth;
            total += (double) a[i] * a[i];
        }
        assertTrue("low share " + low / total, low / total > 0.3);
    }

    @Test public void sweepRisesLogarithmically() {
        short[] s = Tones.sweep(20, 20000, 20);
        assertEquals(20 * Tones.RATE, s.length);
        int mid = s.length / 2;
        // At half time a log sweep from 20 Hz to 20 kHz is at their geometric mean, about 632 Hz.
        assertEquals(632, frequency(s, mid - 2400, mid + 2400), 40);
        // At the quarter point: 20 * 1000^0.25 = 112 Hz.
        int q = s.length / 4;
        assertEquals(112, frequency(s, q - 4800, q + 4800), 15);
        assertTrue(peak(s) <= Math.ceil(LIMIT));
        assertEquals(0, s[0]);
        assertEquals(0, s[s.length - 1]);
    }

    @Test public void bandSequenceTimeline() {
        short[] s = Tones.bandSequence();
        assertEquals((int) (Tones.bandSequenceSeconds() * Tones.RATE), s.length);
        assertEquals(35.0, Tones.bandSequenceSeconds(), 0);
        assertEquals(0, Tones.bandAt(0.5));
        assertEquals(-1, Tones.bandAt(4.5));
        assertEquals(1, Tones.bandAt(5.5));
        assertEquals(6, Tones.bandAt(30.5));
        assertEquals(-1, Tones.bandAt(35));
        // The signal agrees with the timeline: 250 Hz at 7 s, silence at 4.5 s.
        assertEquals(250, frequency(s, 7 * Tones.RATE, 8 * Tones.RATE), 5);
        assertEquals(0, peak(java.util.Arrays.copyOfRange(s, (int) (4.2 * Tones.RATE), (int) (4.8 * Tones.RATE))));
    }

    @Test public void leftRightIsStereoAndSidesAlternate() {
        short[] s = Tones.leftRight();
        assertEquals((int) (Tones.LR_CYCLES * Tones.LR_CYCLE_SECONDS * Tones.RATE) * 2, s.length);
        long left0 = 0, right0 = 0, left2 = 0, right2 = 0;
        for (int i = 0; i < Tones.RATE; i++) {
            left0 += Math.abs(s[i * 2]);
            right0 += Math.abs(s[i * 2 + 1]);
            left2 += Math.abs(s[(2 * Tones.RATE + i) * 2]);
            right2 += Math.abs(s[(2 * Tones.RATE + i) * 2 + 1]);
        }
        assertTrue(left0 > 0 && right0 == 0);
        assertTrue(left2 == 0 && right2 > 0);
        assertEquals(0, Tones.sideAt(0.5));
        assertEquals(-1, Tones.sideAt(1.5));
        assertEquals(1, Tones.sideAt(2.5));
        assertEquals(0, Tones.sideAt(4.5));
        assertEquals(-1, Tones.sideAt(12));
    }
}
