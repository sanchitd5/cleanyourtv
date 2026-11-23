# tools/

The ADB client and scripts that debloat, install and measure. Each script's usage is in its first lines; read
that before running it. Shell scripts have PowerShell twins (keep them in step).

## Must know

- `tv.py`: the ADB client (probe, shell, screenshot, key events). Needs `TV_HOST`; reaches the TV only after the
  owner asked. Screenshots go outside the repo until reviewed.
- `theme.sh <theme dir> | --reset`: pushes `config.json`, wallpapers, clips and `inputs/` to
  `/sdcard/Android/data/com.sanchitdang.tvhome/files` and restarts Home. Exactly one argument; exit 2 means nothing was
  sent. Run it as `bash tools/theme.sh ...`.
- `calibrate_sound.py`: drives the calibration add-on's test tones and suggests EQ bands (lands with
  `addons/calibration`; may not exist yet). Check `--help`; keep results out of the repo.
- Python is `tools/venv/bin/python` (`python3 -m venv tools/venv`, `pip install -r tools/requirements.txt`). Inline
  `python3 -c` may be blocked: use a script file.
- Disable, never uninstall; never use `tools/disable.sh` on core packages; `FORCE_HOME` and `FORCE_SETUPWRAITH`
  only as AGENTS.md describes. Close ADB afterwards (`tools/adb-closed.py`).
- Tests that drive these scripts use fake TVs (`tests/`): run the matching test after a change.
- The ADB key in `tools/adbkey/` is private and ignored by git.

## Commands

```sh
tools/venv/bin/python tools/tv.py --probe
TV_HOST=<tv ip> bash tools/theme.sh themes/horizon
tools/venv/bin/python -m unittest discover -s tests      # about 6 minutes, 0 failures
```
