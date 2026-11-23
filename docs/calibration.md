# Calibrating picture and sound

The [Calibration add-on](../addons/calibration/) puts test patterns and test sounds on the TV, and
[`tools/calibrate_sound.py`](../tools/calibrate_sound.md) measures the sound with your computer's microphone.
Neither changes a setting: the adjustments are in the TV's own menus. This page is the order to work in and how
to read what you see. Do the picture in the evening with the lights as you watch, and give the TV ten minutes
to warm up.

## Picture

### One input at a time

A TV stores its picture settings per input, and per signal type on each: HDMI 1 in SDR is separate from HDMI 1
in HDR, and the Apps input (the TV's own Android side, where the add-on runs) has its own. So:

- **Apps input (Android).** Install the add-on and use its patterns. The patterns go through Android's normal
  full-range output. Set the picture here first, and use it for an SDR (standard dynamic range) picture.
- **HDMI inputs** (a console, a box, a Blu-ray player). The add-on cannot send a signal to them. Show a test
  video or your console's own calibration screen from that source, and photograph the TV (see below). Use
  the add-on's patterns as the reference for what each should look like.
- **HDR.** An HDR signal switches the TV to a separate HDR store. Calibrate the SDR store with the add-on,
  then adjust HDR with HDR content from the source, and only a little: some TVs grey out certain modes with
  HDR (MaxVivid on one MediaTek set was greyed out whenever HDR was on).

### RGB range on HDMI

A source sends either limited range (black at 16, white at 235) or full range (0 and 255). The TV's HDMI RGB
range setting has to match. A PlayStation 5 in its default Automatic or Limited setting sends limited, so
set the TV to Limited for it. Wrong in one way, blacks look grey and washed out; wrong the other way, shadows
are crushed and whites clip. The PLUGE patterns show it: in the matching setting, the dark bars below the
background vanish and the first lighter ones just appear.

### Order of adjustments

1. **Picture mode.** Pick the mode for the room (Movie, Cinema, Filmmaker or Custom; avoid Vivid and Dynamic). It
   resets other settings, so do it first.
2. **Brightness, with PLUGE** (`pluge_limited` or `pluge_full`). Brightness is black level. Lower it until the
   bars below the background disappear, then raise it until the next bar just shows.
3. **Contrast, with white clip** (`white_clip`). Raise Contrast to where the bars still show distinct
   steps up to the top. If several top bars merge, the whites are clipped; lower it. Local Contrast, Adaptive Luma
   and Dynamic Contrast also clip highlights when set high: on the owner's set they flattened the bright bars until
   they were turned down.
4. **Gamma** (`gamma`). Squint: the square that disappears is the gamma. Choose 2.2 for a bright room; "Dark" on
   some menus (about 2.4) for a dark one.
5. **Colour** (`bars75`, `bars100`, `sat_rgb`, `sat_cmy`). Lower Colour until a saturation row has an
   even step at each stage and the last two do not merge. Tint should leave grey steps grey (`ramp`).
6. **Sharpness** (`sharp`). Lower it until the one-pixel lines have no halo; most TVs look best at the bottom
   third. Make sure all four border lines show (no overscan: Just Scan, Screen Fit or Full Pixel).
7. **Processing off** (`motion`). Noise reduction, MPEG reduction and Dynamic Contrast off; motion smoothing
   (MEMC) low or off and judge it on the motion pattern: edges stay sharp, no halos behind the square.
8. **Uniformity** (`grey5`, `grey50`, `grey100`, the colour fields) is a check, not an adjustment: it shows
   panel flaws you may need to live with.

### Reading the patterns

| Pattern | Look for |
|---|---|
| `pluge_limited`, `pluge_full` | Bars below the background invisible, the next ones faint. All bars invisible means black is crushed; all visible means raised blacks. |
| `white_clip` | Each bar separate from its neighbour. Merged bars at the top: clipping. |
| `ramp` | Smooth, no colour tint, no banding; the 11 steps evenly spaced. |
| `bars75`, `bars100` | Colours distinct, no bleed. Compare with a known-good screen. |
| `sat_rgb`, `sat_cmy` | Even steps; no hue drift along a row. |
| `sharp` | No halos; 1-pixel lines even; border visible all round; circle round (aspect ratio right). |
| `gamma` | The square that matches the checkerboard. |
| `grey5`, `grey50`, `grey100` | Even field, no clouding, no tint. |
| `motion` | Sharp edges, small steps, no soap-opera look at the level you chose. |

Press Up or Down in the add-on to switch the patterns between limited and full levels. Use the one that matches
the way your signal is meant to be seen: the Apps input is normally fine in full.

### Phone photos

A camera sees what you do not: it can compare two settings later and ends the argument. Lock the exposure and
white balance (long-press on the screen), use a fixed distance and angle, a tripod or a stack of books, the room
lighting you watch in, and a time exposure that does not show scan lines (1/50 s or slower). Photograph the same
pattern at each setting and compare them side by side; the screen's shape in the photo is not the point, the
relative brightness and the bars you can tell apart are. A phone's own brightness can fool it: do not use
the photo to set absolute levels, only to see which setting shows more of the near-black or near-white bars.

## Sound

1. Install the add-on, note the equaliser values, turn the volume down (see
   [`tools/calibrate_sound.md`](../tools/calibrate_sound.md)).
2. Run `tools/venv/bin/python -I tools/calibrate_sound.py --target punch --current <seven values>` with the
   computer where you sit.
3. Type the printed settings into the TV's equaliser (7-band: 125, 250, 500 Hz, 1, 2, 4 and 8 kHz).
4. Run again to confirm; the "Add" column should be near zero. Then play music and a film you know, and nudge by ear.
5. Use the add-on's `sweep` to listen for rattles (cabinet, wall mount) and `lr` to check the speakers are
   not crossed.

Targets: `flat` is the room's honest response; `warm` leans to the bass and softens the top; `punch` adds
warm, tight bass and tames the 2 kHz harshness that makes a small TV speaker sound tinny.

### A worked example

An AiPro4K (MediaTek MT9676, Android 14). The TV's own speakers had weak bass, a hard upper midrange and a
thin top. Tones at the same seven bands were cast to the TV, recorded with a laptop
microphone at the sofa, and the measured levels relative to 1 kHz were compared with the `punch` target
(warm, tight bass and a tamed 2 kHz). At about 2.5 units per dB the equaliser, whose range is roughly
-50 to +50 units, ended up here:

| Band | 125 Hz | 250 Hz | 500 Hz | 1 kHz | 2 kHz | 4 kHz | 8 kHz |
|---|---|---|---|---|---|---|---|
| Setting | +24 | +5 | -20 | 0 | -31 | -19 | +5 |

That is a big bass lift, a dip across the middle that stops voices from sounding boxy, a deep cut at 2 kHz
where the speaker was harshest, and a small lift at 8 kHz for air. It sounded better in the room, and the
measurement and the ears agreed. Your TV and room will want different numbers: use them as a feel for the size
of the moves, not as settings to copy.
