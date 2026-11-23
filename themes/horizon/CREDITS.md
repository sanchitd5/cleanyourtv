Photographs taken by NASA astronauts on the International Space Station, from Wikimedia Commons. Each is
cropped to 16:9, resized to 1920x1080 and darkened behind the clock, the buttons and the dock. NASA
photographs are in the public domain; NASA is credited as the source and does not endorse this project.

- 01-northern-europe-sunrise.jpg: "ISS-43 Earth sunrise, aurora and sparling cities in northern Europe" by NASA (ISS Expedition 43), public domain
  https://commons.wikimedia.org/wiki/File:ISS-43_Earth_sunrise,_aurora_and_sparling_cities_in_northern_Europe.jpg
- 02-scandinavia-at-night.jpg: "ISS-43 Southern Scandinavia" by NASA (ISS Expedition 43), public domain
  https://commons.wikimedia.org/wiki/File:ISS-43_Southern_Scandinavia.jpg
- 03-aurora-red-and-green.jpg: "The aurora borealis blankets the Earth (iss072e159172)" by NASA (ISS Expedition 72), public domain
  https://commons.wikimedia.org/wiki/File:The_aurora_borealis_blankets_the_Earth_(iss072e159172).jpg
- 04-orbital-sunset.jpg: "Sunset from the ISS" by NASA (ISS Expedition 23), public domain
  https://commons.wikimedia.org/wiki/File:Sunset_from_the_ISS.JPG
- 05-nile-at-night.jpg: "Egypt’s Nile River stretches from Luxor to Cairo (iss073e0422271)" by NASA (ISS Expedition 73), public domain
  https://commons.wikimedia.org/wiki/File:Egypt%E2%80%99s_Nile_River_stretches_from_Luxor_to_Cairo_(iss073e0422271).jpg
- 06-moscow-aurora-moon.jpg: "ISS-39 Moscow, Aurora borealis and Moon" by NASA (ISS Expedition 39), public domain
  https://commons.wikimedia.org/wiki/File:ISS-39_Moscow,_Aurora_borealis_and_Moon.jpg
- 07-airglow-milky-way.jpg: "A reddish airglow blankets Earth beneath the Milky Way (iss073e0982696)" by NASA (ISS Expedition 73), public domain
  https://commons.wikimedia.org/wiki/File:A_reddish_airglow_blankets_Earth_beneath_the_Milky_Way_(iss073e0982696).jpg
- 08-aurora-australis.jpg: "ISS-62 Aurora australis over the Indian Ocean" by NASA (ISS Expedition 62), public domain
  https://commons.wikimedia.org/wiki/File:ISS-62_Aurora_australis_over_the_Indian_Ocean.jpg
- 09-northeastern-europe.jpg: "ISS-62 City lights of northeastern Europe" by NASA (ISS Expedition 62), public domain
  https://commons.wikimedia.org/wiki/File:ISS-62_City_lights_of_northeastern_Europe.jpg
- 10-aurora-over-canada.jpg: "ISS-46 Aurora over nocturnal Canada" by NASA (ISS Expedition 46), public domain
  https://commons.wikimedia.org/wiki/File:ISS-46_Aurora_over_nocturnal_Canada.jpg

These photos are in the public domain, not under the repository's MIT licence.

## Video clips (`wallpaper_video`)

The five `wallpapers/*.mp4` clips are cut from one NASA film, a time-lapse of Earth from the International
Space Station. NASA video is in the public domain; NASA is credited as the source and does not endorse this
project. None of it is Apple's aerial footage.

- Film: "Earth from Space in 4K – Expedition 65 Edition", NASA Johnson Space Center, published 2022-08-05,
  shot from the station between 17 April and 17 October 2021 (ISS Expedition 65)
- NASA ID: `jsc2022m000172_Earth_in_4K_Expedition_65_Edition`
- Page: https://images.nasa.gov/details/jsc2022m000172_Earth_in_4K_Expedition_65_Edition
- File used (4096x2160, 4.17 GB):
  https://images-assets.nasa.gov/video/jsc2022m000172_Earth_in_4K_Expedition_65_Edition/jsc2022m000172_Earth_in_4K_Expedition_65_Edition~orig.mp4

| Clip | From the film at | Length | Brightness |
|---|---|---|---|
| 01-aurora-to-sunrise.mp4 | 1:58 | 60 s | as shot |
| 02-city-lights-and-aurora.mp4 | 14:26 | 68 s | as shot |
| 03-aurora-over-night-clouds.mp4 | 18:15 | 43 s | as shot |
| 04-storm-and-coast.mp4 | 13:06 | 60 s | 60 % |
| 05-day-into-night.mp4 | 19:10 | 68 s | 78 % |

Each is scaled to 1080 lines and cut at the sides to 1920x1080, re-encoded as H.264 High 4.1 at 30 fps with
no sound track, and made to loop: its last second fades into the second before its first frame. The two
daytime clips are darkened so the white clock and buttons stay readable. NASA's film does not say where each
pass is, so the captions describe what is on screen rather than name a place. `make_clips.sh`
is the exact command.

The clips are not in the repository (about 200 MB): run `bash make_clips.sh` here once (it downloads NASA's
4.17 GB original and needs ffmpeg), then push the theme. Without them Home shows the stills, or the Apple TV
aerials of the `wallpaper-feeds` add-on with `wallpaper_feeds`.

No input logo ships with the theme: brand logos are their owners' artwork. Put your own in `inputs/` and name
it in `input_logos` (see themes/README.md).
