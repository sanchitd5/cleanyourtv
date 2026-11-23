"""tools/calibrate_sound.py: band measurement and the recommendation maths, on synthetic WAV files.

No microphone and no TV: the recordings are generated here with the standard library. A recording is the seven
tones of the Calibration add-on (4 s each, 1 s of silence) after a lead of silence, each band at a chosen gain,
so the measured levels and the settings can be checked against known answers.
"""
import contextlib
import io
import math
import random
import sys
import tempfile
import unittest
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import calibrate_sound as cs  # noqa: E402

RATE = 24000


def sequence(gains_db, lead=2.3, noise=0.0, tail=1.0, seed=1):
    """Samples of the tone sequence: band k at -20 dBFS plus gains_db[k], after `lead` seconds of silence."""
    rnd = random.Random(seed)
    out = [0.0] * int(lead * RATE)
    for band, gain in zip(cs.BANDS, gains_db):
        amp = 10 ** ((-20 + gain) / 20)
        n = int(cs.TONE_S * RATE)
        ramp = int(0.05 * RATE)
        for i in range(n):
            env = min(1.0, i / ramp, (n - 1 - i) / ramp)
            out.append(amp * env * math.sin(2 * math.pi * band * i / RATE))
        out.extend([0.0] * int(cs.GAP_S * RATE))
    out.extend([0.0] * int(tail * RATE))
    if noise:
        out = [x + rnd.uniform(-noise, noise) for x in out]
    return out


def write_wav(path, samples, channels=1, width=2, rate=RATE):
    scale = {1: 127, 2: 32767, 3: 8388607, 4: 2147483647}[width]
    frames = bytearray()
    for x in samples:
        v = int(round(max(-1, min(1, x)) * scale))
        for _ in range(channels):
            if width == 1:
                frames += bytes([v + 128])
            else:
                frames += v.to_bytes(width, "little", signed=True)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(channels)
        w.setsampwidth(width)
        w.setframerate(rate)
        w.writeframes(bytes(frames))


class MeasureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gains = [6, 3, -2, 0, -4, 2, 5]
        cls.samples = sequence(cls.gains)

    def test_goertzel_reads_a_sine_in_dbfs(self):
        sine = [0.25 * math.sin(2 * math.pi * 1000 * i / RATE) for i in range(RATE)]
        self.assertAlmostEqual(cs.goertzel_db(sine, RATE, 1000, 0, RATE), 20 * math.log10(0.25), delta=0.05)
        self.assertLess(cs.goertzel_db(sine, RATE, 2000, 0, RATE), -80)

    def test_the_first_tone_is_found_after_the_lead(self):
        self.assertAlmostEqual(cs.find_onset(self.samples, RATE), 2.3, delta=0.15)

    def test_a_silent_recording_is_refused(self):
        with self.assertRaises(cs.CalibrationError):
            cs.find_onset([0.0] * RATE, RATE)

    def test_levels_follow_the_gains(self):
        levels = cs.measure(self.samples, RATE, 2.3)
        for (band, level, noise), gain in zip(levels, self.gains):
            self.assertAlmostEqual(level, -20 + gain, delta=0.3, msg=f"{band} Hz")
            self.assertLess(noise, -70)

    def test_a_short_recording_names_the_problem(self):
        with self.assertRaises(cs.CalibrationError) as ctx:
            cs.measure(self.samples[: RATE * 20], RATE, 2.3)
        self.assertIn("record longer", str(ctx.exception))


class RecommendTest(unittest.TestCase):
    def levels(self, rel):
        """measure()'s shape for tones at `rel` dB against the 1 kHz one, with a quiet noise floor."""
        return [(b, -20 + r, -90) for b, r in zip(cs.BANDS, rel)]

    def test_flat_target_undoes_the_measured_tilt_in_units(self):
        rows = cs.recommend(self.levels([6, 3, -2, 0, -4, 2, 5]), "flat", units_per_db=2.5)
        self.assertEqual([r["units"] for r in rows], [-15, -8, 5, 0, 10, -5, -12])   # -rel * 2.5, rounded
        self.assertEqual(rows[3]["gain_db"], 0)

    def test_punch_target_wants_bass_and_a_tamed_2k(self):
        rows = cs.recommend(self.levels([0] * 7), "punch", units_per_db=2.5)
        self.assertEqual(rows[0]["units"], 15)      # +6 dB at 125 Hz
        self.assertEqual(rows[4]["units"], -8)      # -3 dB at 2 kHz: -7.5 rounds to -8
        self.assertEqual(rows[3]["units"], 0)
        self.assertEqual(cs.TARGETS["punch"][0], 6)
        self.assertEqual(cs.TARGETS["punch"][4], -3)

    def test_the_answer_is_relative_to_the_current_setting(self):
        rows = cs.recommend(self.levels([0] * 7), "flat", current=[24, 5, -20, 0, -31, -19, 5])
        self.assertEqual([r["units"] for r in rows], [24, 5, -20, 0, -31, -19, 5])
        rows = cs.recommend(self.levels([2, 0, 0, 0, 0, 0, 0]), "flat", current=[24, 5, -20, 0, -31, -19, 5])
        self.assertEqual(rows[0]["units"], 19)       # 24 - 2 dB * 2.5

    def test_the_setting_is_clamped_to_fifty(self):
        rows = cs.recommend(self.levels([-30, 0, 0, 0, 30, 0, 0]), "flat", units_per_db=2.5)
        self.assertEqual(rows[0]["units"], 50)
        self.assertEqual(rows[4]["units"], -50)
        self.assertTrue(rows[0]["clamped"] and rows[4]["clamped"])
        self.assertFalse(rows[1]["clamped"])

    def test_units_per_db_scales_the_answer(self):
        rows = cs.recommend(self.levels([0] * 7), "punch", units_per_db=1.0)
        self.assertEqual(rows[0]["units"], 6)

    def test_the_wrong_number_of_current_values_is_refused(self):
        with self.assertRaises(cs.CalibrationError):
            cs.recommend(self.levels([0] * 7), "flat", current=[0, 0])

    def test_a_noisy_band_is_flagged_in_the_table(self):
        levels = self.levels([0] * 7)
        levels[0] = (125, -20, -25)
        text = cs.table(cs.recommend(levels, "flat"))
        self.assertIn("noisy", text)
        self.assertEqual(text.count("noisy"), 1)


class ReadWavTest(unittest.TestCase):
    def test_every_width_and_stereo_read_back(self):
        samples = [0.5 * math.sin(2 * math.pi * 440 * i / RATE) for i in range(2000)]
        with tempfile.TemporaryDirectory() as d:
            for width in (1, 2, 3, 4):
                for channels in (1, 2):
                    path = Path(d) / f"w{width}c{channels}.wav"
                    write_wav(path, samples, channels, width)
                    data, rate = cs.read_wav(path)
                    self.assertEqual(rate, RATE)
                    self.assertEqual(len(data), len(samples))
                    self.assertAlmostEqual(data[100], samples[100], delta=0.02 if width == 1 else 0.001)

    def test_a_file_that_is_not_a_wav_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "x.wav"
            path.write_bytes(b"not audio")
            with self.assertRaises(cs.CalibrationError):
                cs.read_wav(path)
            with self.assertRaises(cs.CalibrationError):
                cs.read_wav(Path(d) / "missing.wav")


class CommandLineTest(unittest.TestCase):
    def run_main(self, *argv):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                code = cs.main(list(argv))
            except SystemExit as e:
                code = e.code
        return code, out.getvalue(), err.getvalue()

    def test_offline_wav_prints_the_table_and_changes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "rec.wav"
            write_wav(path, sequence([4, 0, 0, 0, -3, 0, 0], noise=0.001))
            code, out, err = self.run_main("--wav", str(path), "--target", "flat", "--units-per-db", "2")
        self.assertEqual(code, 0, err)
        self.assertIn("125 Hz -8", out)       # 4 dB too loud at 125 Hz: -8 units at 2 per dB
        self.assertIn("2000 Hz +6", out)
        self.assertIn("Nothing was changed on the TV", out)

    def test_offset_overrides_the_search(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "rec.wav"
            write_wav(path, sequence([0] * 7, lead=3.0))
            code, out, _ = self.run_main("--wav", str(path), "--offset", "3.0", "--target", "punch")
        self.assertEqual(code, 0)
        self.assertIn("125 Hz +15", out)

    def test_a_missing_file_exits_2(self):
        code, _, err = self.run_main("--wav", "/nonexistent/x.wav")
        self.assertEqual(code, 2)
        self.assertIn("cannot read", err)

    def test_bad_arguments_are_refused(self):
        for argv in (["--target", "loud"], ["--current", "1,2,3"], ["--units-per-db", "0"],
                     ["--record-seconds", "10"]):
            code, _, _ = self.run_main(*argv)
            self.assertEqual(code, 2, argv)

    def test_the_recorder_command_is_the_documented_one(self):
        from unittest import mock
        with mock.patch.object(cs.shutil, "which", side_effect=lambda n: "/x/ffmpeg" if n == "ffmpeg" else None), \
                mock.patch.object(cs.sys, "platform", "darwin"):
            cmd = cs.recorder_command(48, "out.wav")
        self.assertEqual(cmd[cmd.index("-f"):cmd.index("-f") + 4], ["-f", "avfoundation", "-i", ":0"])
        with mock.patch.object(cs.shutil, "which", return_value=None):
            self.assertIsNone(cs.recorder_command(48, "out.wav"))


if __name__ == "__main__":
    unittest.main()
