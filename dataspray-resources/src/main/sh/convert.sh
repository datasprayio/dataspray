#!/usr/bin/env bash
#
# Copyright 2024 Matus Faro
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

set -ex

LOGO_MASTER=$1
LOGO_SMALL=$2
LOGO_AND_TITLE_DARK=$3
LOGO_AND_TITLE_LIGHT=$4

SCOUR=scour
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

CONVERT=convert
CONVERT_OPTS=(
  -verbose \
  -background none)
CONVERT_VERSION=$($CONVERT -version | head -n 1)
if [[ ! $CONVERT_VERSION =~ ImageMagick\ 7 ]]; then
  echo "ImageMagick version 7 is required, found $CONVERT_VERSION"
  exit 1
fi

# Main logos
$SCOUR -i "$LOGO_MASTER" -o logo.svg "${SCOUR_OPTS[@]}"
$CONVERT "${CONVERT_OPTS[@]}" "$LOGO_MASTER" logo.png
$CONVERT "${CONVERT_OPTS[@]}" "$LOGO_SMALL" logo-small.png

# Logos with title
$CONVERT "${CONVERT_OPTS[@]}" "$LOGO_AND_TITLE_DARK" logo-and-title-dark.png
$CONVERT "${CONVERT_OPTS[@]}" "$LOGO_AND_TITLE_LIGHT" logo-and-title-light.png

# Web Icon

# SVG
$SCOUR -i "$LOGO_SMALL" -o favicon.svg "${SCOUR_OPTS[@]}"

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
  $CONVERT "${CONVERT_OPTS[@]}" -resize "$SIZE"x"$SIZE" "$SOURCE" "$DEST_SIZE_FILENAME"
  SIZES_FILENAMES+=("$DEST_SIZE_FILENAME")
done
$CONVERT "${CONVERT_OPTS[@]}" -compress zip "${SIZES_FILENAMES[@]}" favicon.ico
rm -f "${SIZES_FILENAMES[@]}"
