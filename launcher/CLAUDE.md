# launcher/

The Horizon home screen for Android TV: plain Java, minSdk 26, AndroidX RecyclerView and DynamicAnimation only,
no network. Application id `com.sanchitdang.tvhome`; Java package stays `com.gathin.tvhome`.

## Must know

- No `INTERNET`, no network classes, no services that poll. `tests/test_launcher_no_network.py` holds the
  manifest permission list (`SHIPPED`) and bans `java.net.`, okhttp, WebView. Downloads belong to add-ons.
- New behaviour is off by default and theme-driven. Fork keys are theme-only unless they have an on-TV row; an
  on-TV choice goes in `Config.HORIZON_KEYS` so "Follow theme" clears it. Full checklist: `.claude/agents/launcher-dev.md`.
- Every string in `res/values/strings.xml` needs a `res/values-iw/strings.xml` twin with the same format
  arguments (`tests/test_hebrew_resources.py`).
- The launcher never mentions the add-ons folder, and `settings.gradle` includes only `:app` (`tests/test_addons.py`).
- Size: release APK is about 739 KB (upstream's 576 KB gate is knowingly exceeded). Do not add drawables, fonts
  or libraries casually; the font files are OFL (`FONT-LICENSE-*.txt`).
- Config is `Config.java` (theme `config.json` overlays `config.xml`; a choice made on the TV wins and survives a
  theme push). New key rows go in `themes/README.md` and the Horizon block of `SCREENSHOT_WANTED` in
  `tests/test_switches.py`.

## Commands

```sh
cd launcher && ./gradlew testReleaseUnitTest lintRelease assembleRelease   # lint: zero errors
STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 bash build-and-install.sh   # needs TV_HOST; tv-deployer's job
```

Unit tests live in `app/src/test/java/com/gathin/tvhome/` and test pure logic (parsing, clamping, fallbacks).
A force-stop of the launcher switches the key helper off; real-remote presses are the proof for helper buttons.
