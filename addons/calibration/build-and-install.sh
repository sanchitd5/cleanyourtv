#!/usr/bin/env bash
# Build the calibration app and install it on the TV. Open it from the app list, or start a pattern or signal
# directly (see README.md).
#
# Usage: TV_HOST=192.168.1.50 addons/calibration/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/calibration/build-and-install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.sanchitdang.calibration
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  tv "pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/calibration.apk >/dev/null
tv "pm install -r /data/local/tmp/calibration.apk; rm -f /data/local/tmp/calibration.apk"
echo "Installed. Open Calibration from the app list, or: tools/venv/bin/python -I tools/tv.py 'am start -n $PKG/com.gathin.calibration.PatternActivity --es pattern bars75'"
