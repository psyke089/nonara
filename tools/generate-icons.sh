#!/usr/bin/env bash
# Regenerates the legacy launcher PNGs (API 24–25) from the same design as
# app/src/main/res/drawable/ic_launcher_foreground.xml.
# Requires ImageMagick 7 (`magick`). Intermediates live in ./tmp, which is gitignored.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p tmp
master() {
  magick -size 512x512 xc:none \
    -fill '#3F5D4B' -draw "$2" \
    -fill '#F6F1E7' -draw 'roundrectangle 162,112 350,400 28,28' \
    -fill '#3F5D4B' -draw 'rectangle 162,150 178,400' \
    -fill '#3F5D4B' -draw 'roundrectangle 196,190 316,210 4,4' \
    -fill '#3F5D4B' -draw 'roundrectangle 196,240 316,260 4,4' \
    -fill '#3F5D4B' -draw 'roundrectangle 196,290 270,310 4,4' \
    "$1"
}

master tmp/launcher-master.png 'roundrectangle 0,0 511,511 112,112'
master tmp/launcher-master-round.png 'circle 256,256 504,256'

for pair in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  density=${pair%%:*}
  size=${pair##*:}
  magick tmp/launcher-master.png -resize "${size}x${size}" -strip \
    "app/src/main/res/mipmap-${density}/ic_launcher.png"
  magick tmp/launcher-master-round.png -resize "${size}x${size}" -strip \
    "app/src/main/res/mipmap-${density}/ic_launcher_round.png"
done
