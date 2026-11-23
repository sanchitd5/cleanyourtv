# Windows version of build-and-install.sh: build the calibration app and install it on the TV.
#
# Usage: addons\calibration\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\calibration\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.sanchitdang.calibration'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    Tv "pm uninstall $pkg"
    exit 0
}

Push-Location $here
try {
    & .\gradlew.bat assembleRelease -q
    if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; see launcher\README)' }
} finally { Pop-Location }
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
& $py -I (Join-Path $tools 'b64push.py') $apk /data/local/tmp/calibration.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed' }
Tv 'pm install -r /data/local/tmp/calibration.apk; rm -f /data/local/tmp/calibration.apk'
Write-Host "Installed. Open Calibration from the app list."
