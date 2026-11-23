# Calibration

Test patterns for the picture and test signals for the sound, drawn and generated on the TV itself: no files,
no network. Pair it with [`docs/calibration.md`](../../docs/calibration.md), which says what to adjust, and
[`tools/calibrate_sound.py`](../../tools/calibrate_sound.md), which measures the sound with your computer's
microphone.

Everything is driven with the remote: Left and Right change pattern, Up and Down switch between limited
(16-235) and full (0-255) levels, OK shows or hides the help text, Back leaves.

## Picture patterns

Drawn at the display mode's native size on a fixed-size surface, so one pattern pixel is one panel pixel even
when Android's own UI is 1080p on a 4K panel. The window asks for the default colour mode (no wide gamut).

| Id | What it shows |
|---|---|
| `pluge_limited` | Near-black bars 0, 2, 4 ... 24, 25 on a background of 16 |
| `pluge_full` | Near-black bars 0 to 25 on a background of 0 |
| `white_clip` | Codes 235 to 255, one bar each |
| `ramp` | A smooth grey ramp, one code per step, and an 11-step scale |
| `bars75`, `bars100` | White, yellow, cyan, green, magenta, red, blue at 75% and 100%, over a grey scale |
| `sat_rgb`, `sat_cmy` | Grey to the pure colour in steps of 20%, for the primaries and the secondaries |
| `sharp` | 1-pixel lines, a checkerboard, a crosshair, a circle and a border on the very edge |
| `gamma` | A 1-pixel checkerboard beside solid squares worth gamma 1.8 to 2.6 |
| `grey5`, `grey50`, `grey100`, `red`, `green`, `blue` | Full-field uniformity |
| `motion` | Moving and still squares, stripes and text, for judging motion smoothing |

Each pattern has a paragraph of help (on by default; OK hides it) saying what to adjust and how to read it.

## Sound signals

Generated as 16-bit PCM and played with `AudioTrack`. Peaks are -12 dBFS, every signal fades in and out over
50 ms, and the screen carries a warning to start with the volume low.

| Id | Signal |
|---|---|
| `pink` | 60 seconds of pink noise |
| `bands` | Sine at 125, 250, 500, 1k, 2k, 4k and 8k Hz, 4 s each, 1 s of silence between; the screen names the band |
| `sweep` | A 20 Hz to 20 kHz logarithmic sweep, 20 s |
| `lr` | A 1 kHz beep on the left, then the right, three times |

From the menu a signal waits for OK. Started from a computer it counts down 3 seconds first (Back cancels).

## Start a pattern or signal from a computer

```sh
tools/venv/bin/python -I tools/tv.py 'am start -n com.sanchitdang.calibration/com.gathin.calibration.PatternActivity --es pattern bars75'
tools/venv/bin/python -I tools/tv.py 'am start -n com.sanchitdang.calibration/com.gathin.calibration.SignalActivity --es signal bands'
```

`tools/calibrate_sound.py` does the second for you.

## Install

```sh
TV_HOST=192.168.1.50 addons/calibration/build-and-install.sh
```

Windows: `addons\calibration\build-and-install.ps1 -TvHost 192.168.1.50`. Remove it with `--uninstall`
(`-Uninstall`). Tests: `cd addons/calibration && ./gradlew testReleaseUnitTest` (the level maths and the signal
generators).

## Limits

It only shows the Apps input. HDMI inputs have their own picture settings and a signal this app cannot send:
use a test video or the console's own calibration screens there, and read `docs/calibration.md` for what to
match.

## Permissions

None. No network access (no `INTERNET` permission) and no background service: it runs only while its screens
are open.

## Tested on

Not yet tested on a TV.
