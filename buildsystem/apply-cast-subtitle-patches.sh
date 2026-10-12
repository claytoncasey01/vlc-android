#!/bin/sh
set -eu

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 /path/to/libvlcjni /path/to/vlc" >&2
    exit 1
fi
cast_patch_root=$(CDPATH= cd -- "$(dirname "$0")/patches" && pwd)

apply_cast_patch() {
    cast_source=$1
    cast_patch=$2
    if git -C "$cast_source" apply --reverse --check "$cast_patch" 2>/dev/null; then
        return
    fi
    git -C "$cast_source" apply --check "$cast_patch" || {
        echo "Cast subtitle patch conflicts in $cast_source; sources were not reset." >&2
        exit 1
    }
    git -C "$cast_source" apply "$cast_patch"
}

apply_cast_patch "$2" "$cast_patch_root/vlc/0001-chromecast-external-webvtt.patch"
apply_cast_patch "$2" "$cast_patch_root/vlc/0002-chromecast-embedded-text-subtitles.patch"
apply_cast_patch "$1" "$cast_patch_root/libvlcjni/0001-chromecast-subtitle-bridge.patch"
apply_cast_patch "$1" "$cast_patch_root/libvlcjni/0002-embedded-cast-subtitle-reader.patch"
