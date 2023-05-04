#!/usr/bin/env bash
set -ex

LOGO_MASTER=$1
LOGO_SMALL=$2
LOGO_AND_TITLE_DARK=$3
LOGO_AND_TITLE_LIGHT=$4

SCOUR_OPTS=(
  --verbose \
  --shorten-ids \
  --indent=none \
  --remove-titles \
  --remove-descriptions \
  --remove-metadata \
  --remove-descriptive-elements \
  --enable-comment-stripping \
  --enable-viewboxing \
  --enable-id-stripping \
  --indent=none \
  --strip-xml-prolog \
  --strip-xml-space \
  --error-on-flowtext)
CONVERT_OPTS=(
  -verbose \
  -background none)

# Main logos
scour -i "$LOGO_MASTER" -o logo.svg "${SCOUR_OPTS[@]}"
magick convert "${CONVERT_OPTS[@]}" "$LOGO_MASTER" logo.png
magick convert "${CONVERT_OPTS[@]}" "$LOGO_SMALL" logo-small.png

# Logos with title
magick convert "${CONVERT_OPTS[@]}" "$LOGO_AND_TITLE_DARK" logo-and-title-dark.png
magick convert "${CONVERT_OPTS[@]}" "$LOGO_AND_TITLE_LIGHT" logo-and-title-light.png

# Web Icon

# SVG
scour -i "$LOGO_SMALL" -o favicon.svg "${SCOUR_OPTS[@]}"

# ICO
# Sizes taken from https://dev.to/masakudamatsu/favicon-nightmare-how-to-maintain-sanity-3al7#22-creating-an-raw-ico-endraw-file
SIZES_FILENAMES=()
for SIZE in 16 32 48; do
  DEST_SIZE_FILENAME="logo-$SIZE.png"
  # Small sizes use small logo
  if (( SIZE < 40 )); then
      SOURCE="$LOGO_SMALL"
  else
      SOURCE="$LOGO_MASTER"
  fi;
  magick convert "${CONVERT_OPTS[@]}" -resize "$SIZE"x"$SIZE" "$SOURCE" "$DEST_SIZE_FILENAME"
  SIZES_FILENAMES+=("$DEST_SIZE_FILENAME")
done
magick convert "${CONVERT_OPTS[@]}" -compress zip "${SIZES_FILENAMES[@]}" favicon.ico
#rm -f "${SIZES_FILENAMES[@]}"
