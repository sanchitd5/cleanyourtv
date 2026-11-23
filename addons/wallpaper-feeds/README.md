# Wallpaper feeds

Fresh wallpapers for the launcher without giving the launcher a network: this app downloads them and hands
them over, and the launcher only reads files.

- **Apple TV aerials**: the slow drone videos from Apple's screensaver (Apple's public aerial server, the 114
  clips of its current catalogue), played behind Home with `wallpaper_video`.
- **Bing photo of the day**: the last eight, 1920x1080.
- **Wikimedia picture of the day**: the last seven, at 1920 px; portraits and small pictures are skipped.

Each picture and clip is captioned with its place and its credit (and the licence, for Wikimedia), which Home
shows with `wallpaper_captions` and the screensaver with `clock_aerial`.

## How much it keeps

It sizes itself to the TV:

- **Storage.** The TV keeps 4 GB or 15% of its storage free, whichever is more. The clips may take a quarter
  of what is free above that, up to 6 GB and 30 clips. A TV with 8 GB and 6 GB free gets two or three clips;
  one with 50 GB free gets 30. Pictures are small and capped at 30.
- **Quality.** 4K HEVC only on a 4K panel with a 4K HEVC decoder, 3 GB of memory and room for eight 4K clips;
  otherwise 1080p HEVC (about 180 MB a clip) where HEVC decodes, else 1080p H.264.
- **Bandwidth.** It refreshes twice a day, on an unmetered network only, and fetches at most two clips a run.
  A full shelf swaps its oldest clip for a new one once a week.

## Install

Build it on the machine that built the launcher: the two must share a signing key.

```sh
TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh
```

The script builds the app and stages it in the launcher. Home then asks **Install Wallpaper feeds?**; choose
Install and confirm on the TV's installer screen. The app opens and starts its first refresh. (`--adb` installs
it straight away instead.) Then turn it on in the theme's `config.json`:

```json
"wallpaper_feeds": true,
"wallpaper_video": true,
"wallpaper_captions": true
```

Until the first files arrive, or if you remove the app, the theme's own pictures stay. Open **Wallpaper feeds**
from the app list to pick sources, see what the last refresh did, or refresh now.

To remove it: `TV_HOST=192.168.1.50 addons/wallpaper-feeds/build-and-install.sh --uninstall`. That deletes the
downloaded files from the launcher too.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE`: the downloads, from www.bing.com, api.wikimedia.org,
  upload.wikimedia.org/thumb.wikimedia.org and sylvan.apple.com only. HTTPS only; nothing is sent but the
  requests.
- `RECEIVE_BOOT_COMPLETED`: the twice-daily refresh stays scheduled after a reboot.
- `com.sanchitdang.tvhome.permission.WRITE_FEEDS`: the launcher's own signature permission, held only by apps
  signed with the launcher's key. It lets this app write into the launcher's `feeds/` folder and nowhere else.

sylvan.apple.com's certificate comes from Apple's own root, which Android does not carry, so the app trusts
Apple Root CA for that one host (`res/xml/network_security_config.xml`, fingerprint in the comment there).

## Tested on

Not yet tested on a TV.
