# addons/

Optional Android TV apps (module cards), each its own Gradle project, never built into the launcher. Index and
rules: `README.md` here and `docs/workflows/addon.md`. Agent: `.claude/agents/addon-dev.md`.

## Must know (all add-ons)

- Every folder needs `settings.gradle`, `README.md` with a `## Permissions` heading and a Tested on section,
  both wrappers (`gradlew`, `gradlew.bat`), an executable `build-and-install.sh`, a row in `README.md` here, and
  a 16:9 `screenshot.jpg` of at most 300 KB (or a `SCREENSHOT_WANTED` entry in `tests/test_addons.py` until it
  exists). `tests/test_addons.py` fails any folder that lacks them, so never create a folder holding only notes.
- No prebuilt APKs. The launcher never references `addons/`.
- Add-ons that need the launcher's provider are signed with the launcher's key; build on the same machine.

## wallpaper-feeds

See `wallpaper-feeds/CLAUDE.md` (downloads, signature permission, Apple root trust).

## calibration (`com.sanchitdang.calibration`)

Picture test patterns and test tones, with `tools/calibrate_sound.py` and `docs/calibration.md`. It lands from a
separate branch, so those paths may not exist in a checkout yet; read them before relying on them. Notes:

- It generates patterns and tones only. It never changes the TV's picture or sound menus (vendor apps): the
  `calibration-assistant` agent tells the owner which values to set.
- Patterns are drawn live (no stored pictures); tones are synthesised. Keep it offline: no `INTERNET`.
- The same folder rules above apply the moment `addons/calibration/` exists: `settings.gradle`, README with
  Permissions and Tested on, a row in `README.md` here, screenshot or `SCREENSHOT_WANTED`.
- Results are logged locally by the owner, never in the repo, unless anonymised as a worked example in
  `docs/calibration.md`.
- Picture is per input and per dynamic range (SDR, HDR10, Dolby Vision, HLG); the HDMI RGB range must match the
  source (Limited 16-235 or Full 0-255).
