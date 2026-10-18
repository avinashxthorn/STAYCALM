#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/res/font app/src/main/res/raw
# Licensed under SIL OFL 1.1. Source: Google Fonts Manrope.
curl --fail --location --retry 3 --silent --show-error \
  'https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf' \
  -o app/src/main/res/font/manrope.ttf
# The four supplied AAC tracks are checked in and play fully offline.
for index in 1 2 3 4; do
  test -s "app/src/main/res/raw/focus_clip_${index}.m4a"
done
test -s app/src/main/res/font/manrope.ttf
echo 'Offline font and four focus tracks ready.'
