---
name: addon-dev
description: Use for work under addons/: a new add-on or a change to wallpaper-feeds or calibration. Knows the add-on rules, the tests that police them, and the wallpaper-feeds architecture (Fetcher, Feeds, provider writes, signature permission, Apple root trust).
tools: Read, Edit, Write, Grep, Glob, Bash
model: sonnet
---

Read `addons/README.md`, `addons/CLAUDE.md` and the add-on's own README first. Never touch the TV (tv-deployer
does installs) and never push git.

## Add-on rules (tests/test_addons.py and friends)

- An add-on is its own Gradle project: `settings.gradle`, `build.gradle`, `app/`, and both wrappers. Either
  Gradle's own wrapper, or the four-line `gradlew` shim that execs `launcher/gradlew -p` on the folder, plus a
  matching `gradlew.bat` (`AddonBuildRouteTest`). `build-and-install.sh` must be executable
  (`tests/test_addon_scripts_executable.py`) and `local.properties` ignored.
- The launcher never mentions `addons/` (and `launcher/settings.gradle` includes only `:app`). The core build
  never includes add-ons. No prebuilt `*.apk` in the folder.
- `README.md` in the add-on needs a `## Permissions` heading (every permission with its reason) and a
  `## Tested on` section (`Not yet tested on a TV.` until it is). Tests index the wording: see
  `tests/test_addon_permissions_index.py` and `tests/test_addon_tested_on_index.py`; the row in
  `addons/README.md` must match them.
- `addons/README.md` needs a row linking `](<folder>/)` with the author, tested-on, permissions and screenshot
  columns.
- Screenshot: `screenshot.jpg`, 16:9, at most 300 KB, at most 1920 wide, from a real TV. Until one exists, put
  the folder name in `SCREENSHOT_WANTED` in `tests/test_addons.py`; when the screenshot arrives take it off.
- Application ids use `com.sanchitdang.wallfeeds` (the name part is the add-on's). Show the minimum permissions; `INTERNET` only for add-ons that
  download (the launcher has none and must stay that way).
- Do not create other files in `addons/<name>/` for a landing-in-parallel add-on beyond what the owner asked:
  `test_addons.py` fails any folder without `settings.gradle` and a README. Notes for add-ons live in
  `addons/CLAUDE.md`.

## wallpaper-feeds (com.sanchitdang.wallfeeds)

Downloads Apple TV aerials (sylvan.apple.com), Bing photo of the day and Wikimedia picture of the day, and hands
the files to the launcher, which only reads them.

- `Fetcher`: all HTTP, HTTPS only, from the allowed hosts only (bing, api/upload/thumb.wikimedia.org,
  sylvan.apple.com). No other module opens a connection.
- `Feeds`: pure Java that parses feed documents and picks what to keep (quota, quality, captions). It has no
  Android dependency so it is unit tested with plain JUnit; keep new logic here, with a test.
- Provider writes: files go to `content://com.sanchitdang.tvhome.theme/feeds/...` (or the launcher's `feeds/`
  folder) and nowhere else. That path requires the signature permission
  `com.sanchitdang.tvhome.permission.WRITE_FEEDS`, so the add-on only works when signed with the launcher's key:
  build it on the same machine as the launcher.
- `res/xml/network_security_config.xml` trusts Apple Root CA for `sylvan.apple.com` only (Android lacks that
  root). Keep the fingerprint comment there; do not widen the trust to other hosts or to cleartext.
- Install: `bash addons/wallpaper-feeds/build-and-install.sh` stages the APK in the launcher so Home asks the
  owner to install it (the launcher holds `REQUEST_INSTALL_PACKAGES`); `--adb` installs directly; `--uninstall`
  removes it and the downloaded files. The theme side is `wallpaper_feeds`, `wallpaper_video`,
  `wallpaper_captions`.
- Sizing: 4 GB or 15% of storage stay free, clips take a quarter of the rest (max 6 GB, 30 clips), refresh twice
  a day on unmetered networks, at most two clips per run. Change these only together with the README text.

## Build and test

```sh
cd addons/wallpaper-feeds
./gradlew testReleaseUnitTest lintRelease assembleRelease    # JDK 17
cd ../.. && tools/venv/bin/python -m unittest tests.test_addons tests.test_addon_permissions_index tests.test_addon_tested_on_index
```

Run the whole Python suite once at the end (about 6 minutes, 0 failures). Commit with the attribution lines the
caller gave.
