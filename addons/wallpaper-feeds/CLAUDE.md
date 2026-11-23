# addons/wallpaper-feeds

Android app `com.sanchitdang.wallfeeds`: downloads Apple TV aerials, the Bing and Wikimedia pictures of the day,
and hands them to the launcher, which stays offline and only reads files. Theme keys: `wallpaper_feeds`,
`wallpaper_video`, `wallpaper_captions`.

## Must know

- This is the only place that touches the network. `Fetcher` does all HTTP: HTTPS only, hosts limited to
  bing, api/upload/thumb.wikimedia.org and sylvan.apple.com. Keep the README Permissions text in step.
- `Feeds` is pure Java (parsing, quota, quality picks, captions) with plain JUnit tests; put new logic there.
- Writes go to the launcher's provider `content://com.sanchitdang.tvhome.theme/feeds/...`, guarded by the signature
  permission `com.sanchitdang.tvhome.permission.WRITE_FEEDS`. Build on the same machine and key as the launcher or
  the app cannot write.
- `res/xml/network_security_config.xml` trusts Apple Root CA for `sylvan.apple.com` only. Do not widen it.
- README must keep a `## Permissions` heading and `## Tested on`; there is no `screenshot.jpg` yet, so the folder
  is in `SCREENSHOT_WANTED` in `tests/test_addons.py` (remove it when a TV screenshot lands).
- Storage and bandwidth limits are documented in the README ("How much it keeps"); change both together.

## Commands

```sh
cd addons/wallpaper-feeds && ./gradlew testReleaseUnitTest lintRelease assembleRelease
bash addons/wallpaper-feeds/build-and-install.sh          # stages the APK; Home asks to install (TV_HOST set)
bash addons/wallpaper-feeds/build-and-install.sh --adb    # install directly
bash addons/wallpaper-feeds/build-and-install.sh --uninstall
```
