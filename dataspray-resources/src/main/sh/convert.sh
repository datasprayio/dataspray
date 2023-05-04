#!/usr/bin/env bash
set -ex

LOGO_MASTER=$1
LOGO_AND_TITLE_DARK=$2
LOGO_AND_TITLE_LIGHT=$3

scour -i $LOGO_MASTER -o logo.svg \
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
  --error-on-flowtext
magick convert -verbose -background none "$LOGO_MASTER" logo.png
magick convert -verbose -background none "$LOGO_AND_TITLE_DARK" logo-and-title-dark.png
magick convert -verbose -background none "$LOGO_AND_TITLE_LIGHT" logo-and-title-light.png
magick convert -verbose -background none -define 'icon:auto-resize=16,32,96'  $LOGO_MASTER favicon.ico
