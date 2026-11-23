#!/usr/bin/env python3
"""Measure how a TV sounds at the seven bands of its equaliser, and print the EQ settings that bring it to a target.

    tools/venv/bin/python -I tools/calibrate_sound.py --target punch
    tools/venv/bin/python -I tools/calibrate_sound.py --wav recording.wav --target warm --current 0,0,0,0,0,0,0

It starts the Calibration add-on's tone sequence on the TV (125, 250, 500 Hz, 1, 2, 4 and 8 kHz, four seconds
each, a second of silence between), records it with this computer's microphone (ffmpeg, else sox), measures each
band against the 1 kHz tone and prints a table of recommended settings in the TV's own units. It never changes
a setting: the equaliser lives in the TV's menus, so you type the numbers in.

  --target flat|warm|punch   the curve to aim for, in dB relative to 1 kHz (punch: +6 dB at 125 Hz, -3 dB at 2 kHz)
  --units-per-db N           TV units per dB of gain, default 2.5; the result is clamped to -50..50
  --current A,B,C,D,E,F,G    the settings the TV had while recording (default all 0); the answer is relative to them
  --wav FILE                 analyse a recording you made yourself instead of recording one
  --no-launch                record only; you start the tones (or the TV is already playing them)
  --record-seconds N         how long to record, default 48
  --offset SECONDS           where the first tone starts in the recording, instead of finding it

Needs the Calibration add-on on the TV (addons/calibration) and TV_HOST for the launch. Standard library only.
Exit status: 0 done, 2 it could not record or read the file. Read tools/calibrate_sound.md first.
"""
import argparse
import array
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import wave

BANDS = (125, 250, 500, 1000, 2000, 4000, 8000)
TONE_S, GAP_S = 4.0, 1.0
SLOT_S = TONE_S + GAP_S
SEQUENCE_S = SLOT_S * len(BANDS)
REFERENCE = 1000
EQ_LIMIT = 50
# Target response in dB relative to the 1 kHz tone, at BANDS.
TARGETS = {
    "flat": (0, 0, 0, 0, 0, 0, 0),
    "warm": (4, 2, 0, 0, -2, -3, -3),
    "punch": (6, 3, 0, 0, -3, -1, 0),
}
TRIM_S = 0.6            # leave out the start and end of each tone: ramps, room build-up and decay
MIN_SNR_DB = 10.0
START_COMMAND = ("am start -n com.sanchitdang.calibration/com.gathin.calibration.SignalActivity "
                 "--es signal bands")
HERE = Path(__file__).resolve().parent


class CalibrationError(Exception):
    pass


def read_wav(path):
    """(samples as floats in -1..1, mono mix, sample rate) of a PCM WAV file."""
    try:
        with wave.open(str(path), "rb") as w:
            channels, width, rate, frames = w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()
            raw = w.readframes(frames)
    except (OSError, EOFError, wave.Error) as e:
        raise CalibrationError(f"cannot read {path} as a PCM WAV file: {e}")
    if width == 1:
        data = [(b - 128) / 128.0 for b in raw]
    elif width == 2:
        a = array.array("h")
        a.frombytes(raw[: len(raw) // 2 * 2])
        if sys.byteorder == "big":
            a.byteswap()
        data = [s / 32768.0 for s in a]
    elif width == 3:
        data = [int.from_bytes(raw[i:i + 3], "little", signed=True) / 8388608.0
                for i in range(0, len(raw) - 2, 3)]
    elif width == 4:
        data = [s / 2147483648.0 for s in struct.unpack("<%di" % (len(raw) // 4), raw[: len(raw) // 4 * 4])]
    else:
        raise CalibrationError(f"{path}: {width * 8}-bit samples are not supported")
    if channels > 1:
        data = [sum(data[i:i + channels]) / channels for i in range(0, len(data) - channels + 1, channels)]
    if not data or rate <= 0:
        raise CalibrationError(f"{path} holds no audio")
    return data, rate


def goertzel_db(samples, rate, freq, start, stop):
    """Level in dBFS of the sine at `freq` in samples[start:stop] (a full-scale sine reads 0)."""
    n = stop - start
    if n <= 0:
        raise CalibrationError("a measurement window is empty: the recording is too short")
    w = 2 * math.pi * freq / rate
    coeff = 2 * math.cos(w)
    s1 = s2 = 0.0
    for i in range(start, stop):
        s0 = samples[i] + coeff * s1 - s2
        s2, s1 = s1, s0
    power = s1 * s1 + s2 * s2 - coeff * s1 * s2
    amplitude = 2 * math.sqrt(max(power, 0.0)) / n
    return 20 * math.log10(max(amplitude, 1e-12))


def find_onset(samples, rate, window=0.05):
    """Seconds into the recording where the first tone starts: the first 50 ms louder than a third of the peak."""
    step = max(1, int(rate * window))
    rms = []
    for i in range(0, len(samples) - step + 1, step):
        chunk = samples[i:i + step]
        rms.append(math.sqrt(sum(x * x for x in chunk) / step))
    if not rms or max(rms) < 1e-4:
        raise CalibrationError("the recording is silent: is the microphone on, and the TV playing?")
    threshold = max(rms) / 3
    for k, level in enumerate(rms):
        if level >= threshold:
            return k * step / rate
    raise CalibrationError("no tone found in the recording")


def measure(samples, rate, onset):
    """[(band, level dBFS, noise dBFS)] for the seven tones starting at `onset` seconds."""
    if onset + SEQUENCE_S - GAP_S > len(samples) / rate + 0.05:
        have = len(samples) / rate
        raise CalibrationError(f"the recording ends at {have:.1f} s but the tones need until "
                               f"{onset + SEQUENCE_S - GAP_S:.1f} s: record longer, or the tones did not start")
    out = []
    for k, band in enumerate(BANDS):
        t0 = onset + k * SLOT_S
        a, b = int((t0 + TRIM_S) * rate), int((t0 + TONE_S - TRIM_S) * rate)
        level = goertzel_db(samples, rate, band, a, min(b, len(samples)))
        # The silence after the tone, at the same frequency, is the room's noise floor there.
        g0, g1 = int((t0 + TONE_S + 0.4) * rate), int((t0 + TONE_S + GAP_S - 0.1) * rate)
        noise = goertzel_db(samples, rate, band, g0, g1) if g1 <= len(samples) else None
        out.append((band, level, noise))
    return out


def recommend(levels, target, units_per_db=2.5, current=None):
    """One row per band: the measured level relative to 1 kHz, the target, the gain to add and the EQ setting.

    `levels` is measure()'s output. The setting is the one the TV had while recording plus the gain in units,
    rounded and clamped to -50..50; `clamped` says it hit the limit and `snr` the margin over the noise floor.
    """
    if current is None:
        current = [0] * len(BANDS)
    if len(current) != len(BANDS):
        raise CalibrationError(f"--current needs {len(BANDS)} numbers, one per band")
    ref = dict((b, l) for b, l, _ in levels)[REFERENCE]
    rows = []
    for (band, level, noise), want, now in zip(levels, TARGETS[target], current):
        rel = level - ref
        gain = want - rel
        raw = now + gain * units_per_db
        units = max(-EQ_LIMIT, min(EQ_LIMIT, int(round(raw))))
        rows.append({"band": band, "rel_db": rel, "target_db": want, "gain_db": gain, "current": now,
                     "units": units, "clamped": units != int(round(raw)),
                     "snr": None if noise is None else level - noise})
    return rows


def table(rows):
    lines = ["  Band    Now(dB)  Target  Add(dB)   EQ now -> set   Note",
             "  ------  -------  ------  -------  --------------  ----"]
    for r in rows:
        note = []
        if r["clamped"]:
            note.append("at the limit")
        if r["snr"] is not None and r["snr"] < MIN_SNR_DB:
            note.append(f"noisy ({r['snr']:.0f} dB over the room): repeat in a quieter room, closer or louder")
        label = f"{r['band'] // 1000} kHz" if r["band"] >= 1000 else f"{r['band']} Hz"
        lines.append(f"  {label:<6}  {r['rel_db']:+7.1f}  {r['target_db']:+6.1f}  {r['gain_db']:+7.1f}  "
                     f"{r['current']:>6} -> {r['units']:<+5}  {'; '.join(note)}")
    lines.append("")
    lines.append("  Settings: " + ", ".join(f"{r['band']} Hz {r['units']:+d}" for r in rows))
    return "\n".join(lines)


def recorder_command(seconds, out):
    """The command that records `seconds` of mono 48 kHz audio to `out`, or None when no recorder is installed."""
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg:
        if sys.platform == "darwin":
            source = ["-f", "avfoundation", "-i", ":0"]
        elif sys.platform.startswith("linux"):
            source = ["-f", "alsa", "-i", "default"]
        else:
            source = None
        if source:
            return [ffmpeg, "-y", "-loglevel", "error", *source, "-t", str(seconds), "-ac", "1", "-ar", "48000", out]
    for name in ("rec", "sox"):
        path = shutil.which(name)
        if path:
            lead = [path] if name == "rec" else [path, "-d"]
            return [*lead, "-q", "-r", "48000", "-c", "1", out, "trim", "0", str(seconds)]
    return None


def start_tones():
    """Start the tone sequence on the TV through tv.py. Needs TV_HOST."""
    cmd = [sys.executable, "-I", str(HERE / "tv.py"), START_COMMAND]
    done = subprocess.run(cmd, capture_output=True, text=True)
    if done.returncode:
        raise CalibrationError("could not start the tones on the TV: "
                               + (done.stderr.strip() or done.stdout.strip() or f"tv.py exited {done.returncode}"))


def parse_current(text):
    try:
        values = [int(x) for x in text.split(",")]
    except ValueError:
        raise argparse.ArgumentTypeError("--current is seven whole numbers, like 0,0,0,0,0,0,0")
    if len(values) != len(BANDS):
        raise argparse.ArgumentTypeError("--current needs seven numbers, one per band: 125, 250, 500, 1k, 2k, 4k, 8k")
    return values


def parse_args(argv):
    p = argparse.ArgumentParser(prog="calibrate_sound.py", description="Measure a TV's sound at its EQ bands "
                                "and print the settings that reach a target.")
    p.add_argument("--target", choices=sorted(TARGETS), default="flat")
    p.add_argument("--units-per-db", type=float, default=2.5)
    p.add_argument("--current", type=parse_current, default=None)
    p.add_argument("--wav", help="analyse this recording instead of recording")
    p.add_argument("--no-launch", action="store_true", help="do not start the tones on the TV")
    p.add_argument("--record-seconds", type=float, default=48)
    p.add_argument("--offset", type=float, help="seconds into the recording where the first tone starts")
    p.add_argument("--yes", action="store_true", help="do not wait for Enter before starting")
    args = p.parse_args(argv)
    if args.units_per_db <= 0:
        p.error("--units-per-db must be above 0")
    if args.record_seconds < SEQUENCE_S:
        p.error(f"--record-seconds must cover the {SEQUENCE_S:.0f} s of tones")
    return args


def record(args, out):
    cmd = recorder_command(args.record_seconds, out)
    if cmd is None:
        raise CalibrationError("no recorder found (ffmpeg, sox or rec). Record the tones with your phone or "
                               "computer, save a WAV, and run again with --wav FILE")
    print(f"Recording {args.record_seconds:.0f} s with {Path(cmd[0]).name}. Keep the room quiet.", flush=True)
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        time.sleep(1.5)
        if proc.poll() is not None:
            raise CalibrationError("the recorder stopped at once: " + proc.stderr.read().decode(errors="replace").strip()
                                   + " (on macOS, allow the Terminal in System Settings > Privacy > Microphone)")
        if not args.no_launch:
            start_tones()
            print("Tones started on the TV (3 s countdown, then about 35 s).", flush=True)
        else:
            print("Start the tones on the TV now.", flush=True)
        proc.wait(timeout=args.record_seconds + 20)
    except subprocess.TimeoutExpired:
        proc.kill()
        raise CalibrationError("the recorder did not stop")
    except BaseException:
        proc.kill()
        raise
    if proc.returncode:
        raise CalibrationError("recording failed: " + proc.stderr.read().decode(errors="replace").strip())


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        if args.wav:
            path = args.wav
        else:
            if recorder_command(1, "x") is None:
                raise CalibrationError("no recorder found (ffmpeg, sox or rec). Record the tones with your phone "
                                       "or computer, save a WAV, and run again with --wav FILE")
            if not args.no_launch and not os.environ.get("TV_HOST"):
                raise CalibrationError("set TV_HOST to the TV's address, or use --no-launch / --wav")
            if not args.yes:
                input("Turn the TV volume DOWN to a low level, put the microphone where you sit, then press Enter. ")
            path = str(Path(os.environ.get("TMPDIR", "/tmp")) / f"calibrate-{int(time.time())}.wav")
            record(args, path)
            print(f"Recording kept at {path}; repeat the analysis with --wav.")
        samples, rate = read_wav(path)
        onset = args.offset if args.offset is not None else find_onset(samples, rate)
        levels = measure(samples, rate, onset)
        rows = recommend(levels, args.target, args.units_per_db, args.current)
    except CalibrationError as e:
        print(f"calibrate_sound.py: {e}", file=sys.stderr)
        return 2
    print(f"First tone found at {onset:.1f} s. Target: {args.target}; {args.units_per_db:g} units per dB.\n")
    print(table(rows))
    print("\nNothing was changed on the TV. Enter these in the TV's equaliser, then run again to check.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
