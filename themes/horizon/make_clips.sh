#!/bin/bash
# Cuts the horizon theme's wallpaper_video clips out of NASA's "Earth in 4K - Expedition 65 Edition"
# (nasa_id jsc2022m000172_Earth_in_4K_Expedition_65_Edition, public domain; see CREDITS.md).
#
#   bash make_clips.sh            every clip
#   bash make_clips.sh 03         only the clip whose name starts with 03
#
# Each clip: H.264 High@4.1, 1920x1080 (scaled to 1080 high, the sides cut), 30 fps, yuv420p, no sound track,
# moov first (+faststart), and a loop without a jump: its last second is the source's next second fading into
# the second before the clip's first frame, so the last frame is the frame before the first.
set -eu
cd "$(dirname "$0")"
SRC="jsc2022m000172_Earth_in_4K_Expedition_65_Edition~orig.mp4"
# The 4.17 GB original, once (resumable):
[ -f "$SRC" ] || curl -fL -C - -o "$SRC" "https://images-assets.nasa.gov/video/jsc2022m000172_Earth_in_4K_Expedition_65_Edition/jsc2022m000172_Earth_in_4K_Expedition_65_Edition~orig.mp4"
OUT=wallpapers

# name | start in the film (s) | length (s) | brightness (1 = as shot; day passes are darkened for the white clock)
CLIPS="
01-aurora-to-sunrise|118|60|1
02-city-lights-and-aurora|866|68|1
03-aurora-over-night-clouds|1095|43|1
04-storm-and-coast|786|60|0.6
05-day-into-night|1150|68|0.78
"

for LINE in $CLIPS; do
  IFS='|' read -r NAME START LENGTH LEVEL <<EOF
$LINE
EOF
  case "$NAME" in "${1:-}"*) ;; *) continue ;; esac
  echo "== $NAME: ${LENGTH}s from ${START}s, brightness $LEVEL"
  ffmpeg -v error -stats -y -ss "$START" -t "$((LENGTH + 1))" -i "$SRC" -an -filter_complex "
    [0:v]fps=30,scale=-2:1080:flags=lanczos,crop=1920:1080,format=yuv420p,
         lutyuv=y='clip(16+(val-16)*$LEVEL,16,235)',split=3[a][b][c];
    [a]trim=start=1:end=$LENGTH,setpts=PTS-STARTPTS[main];
    [b]trim=start=$LENGTH:end=$((LENGTH + 1)),setpts=PTS-STARTPTS[tail];
    [c]trim=start=0:end=1,setpts=PTS-STARTPTS[head];
    [tail][head]xfade=transition=fade:duration=1:offset=0[join];
    [main][join]concat=n=2:v=1:a=0[out]" \
    -map "[out]" -c:v libx264 -profile:v high -level:v 4.1 -preset slow -crf 21 -maxrate 6500k -bufsize 13M \
    -g 60 -pix_fmt yuv420p -r 30 -movflags +faststart -map_metadata -1 "$OUT/$NAME.mp4"
done

# Each clip's caption goes into wallpapers/captions.txt once the clip exists (the repository carries only
# the stills' lines, as the clips are not committed).
while IFS= read -r CAP; do
  FILE=${CAP%%	*}
  [ -f "$OUT/$FILE" ] || continue
  grep -q "^$FILE	" "$OUT/captions.txt" 2>/dev/null || printf '%s\n' "$CAP" >> "$OUT/captions.txt"
done <<'CAPTIONS'
01-aurora-to-sunrise.mp4	Aurora over moonlit clouds, then first light · NASA, ISS Expedition 65
02-city-lights-and-aurora.mp4	City lights at night, then an aurora · NASA, ISS Expedition 65
03-aurora-over-night-clouds.mp4	Aurora and city lights at night · NASA, ISS Expedition 65
04-storm-and-coast.mp4	A storm system and coastlines by day · NASA, ISS Expedition 65
05-day-into-night.mp4	Day turning to night over the limb · NASA, ISS Expedition 65
CAPTIONS
