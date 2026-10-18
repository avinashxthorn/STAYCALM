#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/res/font app/src/main/res/raw
# Licensed under SIL OFL 1.1. Source: Google Fonts Manrope.
curl --fail --location --retry 3 --silent --show-error \
  'https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf' \
  -o app/src/main/res/font/manrope.ttf
# Ambient files are offline and looped locally; upstream tracks derived from CC0/Public Domain audio.
# Attribution and upstream documentation: https://github.com/twtrubiks/moonseal/blob/main/public/audio/README.md
for sound in rain ocean birds stream fireplace wind thunder; do
  curl --fail --location --retry 3 --silent --show-error \
    "https://raw.githubusercontent.com/twtrubiks/moonseal/main/public/audio/${sound}.mp3" \
    -o "app/src/main/res/raw/${sound}.mp3"
  test -s "app/src/main/res/raw/${sound}.mp3"
done
test -s app/src/main/res/font/manrope.ttf
echo 'Offline design and audio assets ready.'
