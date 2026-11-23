#!/usr/bin/env bash
# Build Wallpaper feeds and hand it to the TV. By default the APK is staged in the launcher's feeds/ folder and
# Home asks "Install Wallpaper feeds?" the next time it is in front; the TV's installer confirms. --adb installs
# it straight away instead. Either way, then set "wallpaper_feeds": true in the theme (see README.md).
#
# Build it on the machine that built the launcher: the two must share a signing key (the debug key), or the
# launcher refuses its files.
#
# Usage: TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh              # Home asks
#        TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh --adb        # install now
#        TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh --uninstall  # app and its files gone
set -euo pipefail
# File names are matched with ASCII ranges below; pin the locale as tools/disable.sh does (#3286).
export LC_ALL=C
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.wallfeeds
FEEDS=content://com.gathin.tvhome.theme/feeds
TMP=/data/local/tmp/wallfeeds.apk
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # The files it put in the launcher, by the names its captions list, then the app.
  for f in $(tv "content read --uri $FEEDS/captions.txt 2>/dev/null" | cut -f1 | grep -E '^[A-Za-z0-9_.-]+\.(jpg|mp4)$' || true); do
    tv "content delete --uri $FEEDS/$f" >/dev/null
  done
  tv "content delete --uri $FEEDS/captions.txt; content delete --uri $FEEDS/wallfeeds.apk" >/dev/null || true
  tv "pm uninstall $PKG"
  echo "Removed. The theme's own pictures are back after Home's next start."
  exit 0
fi

tv "pm list packages com.gathin.tvhome" | grep -q "package:com.gathin.tvhome$" \
  || { echo "install the launcher first (launcher/build-and-install.sh)" >&2; exit 1; }
(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" "$TMP" >/dev/null
# TCL's TclAppBoot blocks a background job until the app may auto-start; elsewhere the op doesn't exist.
autostart="appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; true"

if [ "${1:-}" = --adb ]; then
  tv "pm install -r $TMP; rm -f $TMP"
  tv "$autostart" >/dev/null
  # Its first screen schedules the refreshes and starts the first one.
  tv "am start -n $PKG/.StatusActivity" >/dev/null
  echo "Installed; the first refresh is running (Wallpaper feeds on the TV shows it)."
else
  tv "content write --uri $FEEDS/wallfeeds.apk < $TMP; rm -f $TMP"
  # Home may need the launcher's leave to hand an APK to the installer; the TV asks for it otherwise.
  tv "appops set com.gathin.tvhome REQUEST_INSTALL_PACKAGES allow 2>/dev/null; true" >/dev/null
  tv "input keyevent KEYCODE_HOME" >/dev/null
  echo "Staged. Home now asks \"Install Wallpaper feeds?\": choose Install, then confirm on the TV's screen."
fi
echo "Then set \"wallpaper_feeds\": true (and \"wallpaper_video\": true for the aerials) in the theme."
