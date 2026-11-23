#!/usr/bin/env bash
# Build Wallpaper feeds, install it on the TV, let it write into the launcher's feeds/ folder and start a first
# refresh. Then set "wallpaper_feeds": true in the theme (see README.md).
#
# Usage: TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh --uninstall   # app and its files gone
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.wallfeeds
FEEDS=content://com.gathin.tvhome.theme/feeds
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # The files it put in the launcher, by the names its captions list, then the app.
  for f in $(tv "content read --uri $FEEDS/captions.txt 2>/dev/null" | cut -f1 | grep -E '^[A-Za-z0-9_.-]+\.(jpg|mp4)$' || true); do
    tv "content delete --uri $FEEDS/$f" >/dev/null
  done
  tv "content delete --uri $FEEDS/captions.txt" >/dev/null || true
  tv "pm uninstall $PKG"
  echo "Removed. The theme's own pictures are back after Home's next start."
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/wallfeeds.apk >/dev/null
tv "pm install -r /data/local/tmp/wallfeeds.apk; rm -f /data/local/tmp/wallfeeds.apk"
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
# TCL's TclAppBoot blocks a background job until the app may auto-start; elsewhere the op doesn't exist.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; true" >/dev/null
# Opening the app schedules the twice-daily refresh; the job runs the first one now.
tv "am start -n $PKG/.StatusActivity" >/dev/null
tv "cmd jobscheduler run -f $PKG 1" >/dev/null || true
echo "Installed; the first refresh is running (open Wallpaper feeds on the TV to watch it)."
echo "Then set \"wallpaper_feeds\": true (and \"wallpaper_video\": true for the aerials) in the theme."
