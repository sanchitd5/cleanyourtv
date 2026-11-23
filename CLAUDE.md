Claude Code reads this file. The guide for every coding agent in this repo is AGENTS.md, imported below.

## Horizon fork

This checkout is "Horizon", a private fork of cleanyourtv that is never upstreamed. Where it differs from
AGENTS.md, this section wins.

- Launcher app id `com.sanchitdang.tvhome`; the Java namespace stays `com.gathin.tvhome`, so components are
  `com.sanchitdang.tvhome/com.gathin.tvhome.MainActivity`, `.../com.gathin.tvhome.HomeKeyService`,
  `.../com.gathin.tvhome.ClockDream`. Theme dir `/sdcard/Android/data/com.sanchitdang.tvhome/files`; theme provider
  `content://com.sanchitdang.tvhome.theme/` (writes need `WRITE_SECURE_SETTINGS`, so adb shell; the `feeds/` path
  also takes the signature permission `com.sanchitdang.tvhome.permission.WRITE_FEEDS`).
- Main theme `themes/horizon` (tvOS look). The owner's private copy, with extra clips and a brand logo, lives outside
  the repo: never commit brand logos or the 196 MB clips (`themes/horizon/make_clips.sh` rebuilds clips).
- Add-ons: `addons/wallpaper-feeds` (`com.sanchitdang.wallfeeds`) and `addons/calibration`
  (`com.sanchitdang.calibration`: picture test patterns and tones, with `tools/calibrate_sound.py` and
  `docs/calibration.md`).
- Build: JDK 17, `./gradlew testReleaseUnitTest lintRelease assembleRelease` in `launcher/` (keep the daemon warm,
  omit `--no-daemon` here). Release APK is about 739 KB: this fork knowingly exceeds the upstream 576 KB gate.
  Python suite: `tools/venv/bin/python -m unittest discover -s tests` (about 6 minutes, must be 0 failures).
- Install: `STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 bash launcher/build-and-install.sh`
  (`TV_HOST` set); themes `bash tools/theme.sh <theme dir>`; add-ons `bash addons/<name>/build-and-install.sh`
  (wallpaper-feeds stages its APK so Home prompts; `--adb` installs directly). Keep the `bash` prefix.
- A force-stop of the launcher switches the accessibility key helper off; `restore_key_services` brings it back on
  the next Home. Injected keys do not prove helper-handled buttons: ask the owner to press the real remote.
- Privacy: no IPs, serials, Wi-Fi names, emails or ADB keys in commits or screenshots. The owner's name is
  allowed only in package ids (`com.sanchitdang.tvhome`) and the theme's watermark text.
- Git: remote `fork` (the owner's GitHub fork) is the only place to push or open PRs; `origin` is upstream and
  gets nothing. Work branch `horizon`; `main` mirrors it.
- Never touch the TV unless the owner asked; only tv-deployer runs adb.

Project agents in `.claude/agents/`: `launcher-dev` (launcher/ changes and new keys), `tv-deployer` (install and
verify over wireless ADB), `theme-designer` (Horizon look, screenshots, counts), `calibration-assistant` (picture
and sound calibration coaching), `addon-dev` (add-on rules, wallpaper-feeds), `release-reviewer` (checklist
before horizon goes to main). Folder notes: `launcher/CLAUDE.md`, `themes/horizon/CLAUDE.md`, `tools/CLAUDE.md`,
`addons/CLAUDE.md`, `addons/wallpaper-feeds/CLAUDE.md`.

@AGENTS.md
