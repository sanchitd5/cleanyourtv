# themes/horizon

The main Horizon theme: a tvOS-style Home (glass dock and panels, date top left and a capsule top right, Inter
font, input tiles grouped left, focus parallax and sheen, looping clips behind Home, drifting screensaver).
`config.json` sets keys documented in `../README.md` (Keys table).

## Must know

- Never commit the clips (`wallpapers/*.mp4`, about 196 MB) or a brand logo. `make_clips.sh` rebuilds the five clips
  from NASA's public domain film (see `CREDITS.md`). The owner's private copy with more clips and a logo is outside
  the repo.
- `wallpapers/captions.txt` is `file<TAB>caption`, and may name only committed files (`tests/test_scenes_captions.py`;
  `make_clips.sh` writes the clip lines). Keep a still picture for every clip.
- The watermark text is the only place the owner's name appears. Keep `config.json` valid and under 64 KB.
- `dock_apps` and `quick_panel` hold this TV's ids (MediaTek picture and sound screens); another TV drops a tile
  whose target does not resolve.
- `screenshot.jpg` comes from the real TV: JPEG, 16:9, at most 300 KB, no Wi-Fi name, avatar or address.
- A new theme changes README theme counts in every translation (`tests/test_readme_theme_count.py`).

## Commands

```sh
tools/venv/bin/python -m json.tool themes/horizon/config.json > /dev/null
bash themes/horizon/make_clips.sh          # all clips; "03" for one; downloads a 4 GB source once
TV_HOST=<tv ip> bash tools/theme.sh themes/horizon         # push (TV_HOST set); --reset removes the overlay
```
