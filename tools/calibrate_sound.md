# calibrate_sound.py

Measures how a TV sounds at the seven bands of its equaliser and prints the settings that bring it to a target
curve. It never changes anything on the TV: the equaliser is in the TV's own menus, so you type the numbers in.

You need the [Calibration add-on](../addons/calibration/) on the TV, a computer with a microphone, and either
`ffmpeg` (macOS and Linux) or `sox`. Without a recorder, record the tones with a phone and pass the file with
`--wav`. Standard library only: nothing to install beyond `tools/venv`.

## Run it

1. Install the add-on: `TV_HOST=<tv ip> addons/calibration/build-and-install.sh`.
2. In the TV's sound menu, pick the sound mode you will use and note the current equaliser values (write seven
   numbers: 125, 250, 500 Hz, 1, 2, 4, 8 kHz). Turn the TV volume **down**.
3. Put the computer where you sit, with the microphone toward the TV and nothing between. Quiet room.
4. Run it:

   ```sh
   TV_HOST=<tv ip> tools/venv/bin/python -I tools/calibrate_sound.py --target punch --current 0,0,0,0,0,0,0
   ```

   It asks for Enter, starts recording, starts the tones on the TV (a 3 second countdown shows on screen), and
   records about 48 seconds. Raise the volume a little during the first tone if the table later says "noisy".
   On macOS, the first run asks to let the terminal use the microphone.

5. Enter the printed settings in the TV's equaliser. Run again with `--current` set to what you entered: the
   table should now show all the "Add" values near zero.

## Options

| Option | Meaning |
|---|---|
| `--target flat\|warm\|punch` | The curve to reach, in dB relative to 1 kHz. `flat` is 0 everywhere. `warm` is +4 dB at 125 Hz, +2 at 250 Hz and -2 to -3 dB from 2 kHz up. `punch` is +6 dB at 125 Hz, +3 at 250 Hz and -3 dB at 2 kHz, with 4 kHz at -1 dB: warm, tight bass and a tamed harsh upper midrange. |
| `--units-per-db N` | How many TV units one dB of gain takes. Default 2.5. Change one band by 10 units, measure again, and divide the change in dB into 10 to find your TV's number. |
| `--current A,B,C,D,E,F,G` | The settings the TV had while recording, so the answer is the new setting and not the change. Default seven zeros. |
| `--wav FILE` | Analyse a recording made yourself (any rate, mono or stereo, 8 to 32 bit PCM). No TV or microphone needed. |
| `--no-launch` | Record only; start the tones yourself (the add-on's `Sound: EQ bands` row, or `am start ... --es signal bands`). |
| `--record-seconds N`, `--offset S` | Recording length (default 48) and where the first tone starts, if the automatic search finds the wrong place. |

Settings are clamped to -50..50; a band at its limit is marked. Bands where the tone is less than 10 dB above the
room's noise are marked "noisy": repeat closer or louder.

## How it measures

Each tone is four seconds; the first and last 0.6 s are left out (ramps, room build-up). The level of the
remaining part is read with a Goertzel filter at the band's frequency, so other sounds do not count. Levels are
shown relative to the 1 kHz tone, so the microphone's own level and the TV volume cancel out. The silence after
each tone, at the same frequency, is the noise floor. The gain to add is the target minus the measured level;
the setting is the current one plus gain times units per dB.

It measures the room as well as the TV, at one position with one microphone, and a laptop microphone is not flat
(it is usually weak below 200 Hz and can be harsh above 4 kHz). Use it to compare before and after on the same
spot, not as an absolute truth; a phone's microphone held in your lap, or a USB measurement microphone, does
better. Then trust your ears. See [docs/calibration.md](../docs/calibration.md) for the worked example.
