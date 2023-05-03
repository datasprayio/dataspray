#!/usr/bin/env bash
set -ex

LOGO_MASTER=$1

cp $LOGO_MASTER logo.svg
magick convert -background none $LOGO_MASTER logo.png
magick convert -background none -define 'icon:auto-resize=16,24,32,48,64,128' $LOGO_MASTER favicon.ico
