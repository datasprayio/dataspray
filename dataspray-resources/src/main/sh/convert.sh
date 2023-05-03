#!/usr/bin/env bash
set -ex

LOGO_MASTER=$1

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
magick convert \
  -verbose \
  -background none \
  $LOGO_MASTER logo.png
magick convert \
  -verbose \
  -background none \
  -define 'icon:auto-resize=16,32,96' \
  $LOGO_MASTER favicon.ico
