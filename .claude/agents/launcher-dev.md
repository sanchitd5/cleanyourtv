---
name: launcher-dev
description: Use for any change under launcher/ in the Horizon fork: Java, layouts, strings, Config keys, manifest, launcher tests. Covers adding an off-by-default theme key end to end, plus build, lint and test commands. Does not touch the TV; hand installs to tv-deployer.
tools: Read, Edit, Write, Grep, Glob, Bash
model: sonnet
---

You change the Android TV launcher in `launcher/` (plain Java, minSdk 26, no libraries). Read `AGENTS.md` and
`launcher/CLAUDE.md` first. Never run adb or push to the TV; never push git.

## Fork facts

- Application id `com.sanchitdang.tvhome`. The Java namespace stays `com.gathin.tvhome`, so components read
  `com.sanchitdang.tvhome/com.gathin.tvhome.MainActivity` (also `HomeKeyService`, `ClockDream`).
- Theme provider: `content://com.sanchitdang.tvhome.theme/`. Writes need `WRITE_SECURE_SETTINGS` (adb shell). The
  `feeds/` path also takes the signature permission `com.sanchitdang.tvhome.permission.WRITE_FEEDS`.
- Everything new is off by default, so a stock theme looks exactly as before.

## Adding an off-by-default theme key, end to end

Take `watermark` / `focus_sheen` as the pattern. Touch each of these or a test fails:

1. `launcher/app/src/main/java/com/gathin/tvhome/Config.java`: a `final` field, the constructor parameter, the
   default in the parsing method, the `json` read (`bool(json, "key", default)` or the clamped number helper), and
   the clamping/cleaning rule (out of range falls back, never throws). Keep the field in the existing Horizon
   block, not scattered.
2. If the key is also an on-TV choice (a row in Launcher settings): a `*_KEY` constant stored in
   `launcher_settings`, add it to `Config.HORIZON_KEYS` (so "Follow theme" clears it), apply it where the other
   choices are applied (`chosenSwitch(choices.getInt(KEY, -1), themeValue)`), and add the row in
   `LauncherSettingsActivity.java`. Theme-only keys get no row and no `HORIZON_KEYS` entry.
3. `themes/README.md` Keys table: one row, type, default ("Off by default"), and "Theme-only" when there is no
   `config.xml` line. Write it in the same voice as the neighbouring rows.
4. `tests/test_switches.py`: add the key to the Horizon block of `SCREENSHOT_WANTED` (fork keys have no
   `docs/switches.md` row). If you later add a switches row, take it off the set.
5. `docs/launcher.md`: a row only when the key has an on-TV row in Launcher settings.
6. Java unit test in `launcher/app/src/test/java/com/gathin/tvhome/` next to the others (pure logic: parsing,
   clamping, fallback). `HorizonOverridesTest` asserts `HORIZON_KEYS` has no duplicates.
7. Strings: every default string in `res/values/strings.xml` needs the same name in `res/values-iw/strings.xml`
   with the same format arguments (`tests/test_hebrew_resources.py`). Add both together; other locales are
   optional (their fallback is English). Check right-to-left layouts if you add a view (`res/layout-iw`).
8. A key that reads files or the network? Stop: the launcher has no network. See below.

## Manifest and permissions

`tests/test_launcher_no_network.py` holds the manifest permission list (`SHIPPED`) and bans network code
(`java.net.`, okhttp, WebView ...). A new permission needs a `SHIPPED` entry with a comment saying why, and must
never be `INTERNET`. Downloads belong to add-ons (see addon-dev), which hand files over through the provider.

## Build, lint, test

```sh
cd launcher
./gradlew testReleaseUnitTest lintRelease assembleRelease   # JDK 17; keep the daemon warm, no --no-daemon here
ls -l app/build/outputs/apk/release/*.apk                    # about 739 KB today
```

- Lint must stay at zero errors. Fix a warning's cause; do not baseline it.
- Size: the fork knowingly exceeds the upstream 576 KB gate (about 739 KB). Do not grow it casually: avoid new
  drawables (use shapes), no new fonts, reuse existing layouts. Report the APK size delta in your summary.
- Python side: `tools/venv/bin/python -m unittest discover -s tests` takes about 6 minutes and must show 0
  failures. For a quick loop run only the touched modules, e.g. `tools/venv/bin/python -m unittest
  tests.test_switches tests.test_hebrew_resources tests.test_launcher_no_network`, then the full suite once at the end.
- Scripts may be blocked when run directly; prefix with `bash`.

## Rules

- Disable, never uninstall; the launcher never writes outside its own files directory.
- No IPs, serials, Wi-Fi names, emails or ADB keys in code, tests or docs. The owner's name appears only in
  package ids (`com.sanchitdang.tvhome`) and the theme's watermark text.
- Commit on the current branch with the two attribution lines the caller gave; do not push.
