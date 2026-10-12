#!/bin/sh
set -eu

: "${ANDROID_SDK:?Set ANDROID_SDK to an Android SDK with platform 36}"
: "${ANDROID_NDK:?Set ANDROID_NDK to an NDK supported for arm64 (r28c was tested)}"

cast_repo_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$cast_repo_root"

# Fetch the pinned sources and apply both native and JNI changes.
buildsystem/compile.sh --init -a arm64
cast_jni_root=${VLC_LIBJNI_PATH:-$cast_repo_root/libvlcjni}
# Archived VideoLAN arm64 contribs with FFmpeg 4.4.5 and libmatroska 1.7.0.
# The original March contrib archive is no longer hosted; do not fetch "latest".
cast_contrib_sha=d17fb017f5d821ad93efbc50ca9ccb40b1b281a5
VLC_PREBUILT_CONTRIBS_URL=${VLC_PREBUILT_CONTRIBS_URL:-https://artifacts.videolan.org/vlc-3.0/android-arm64/vlc-contrib-aarch64-linux-android-$cast_contrib_sha.tar.bz2}
export VLC_PREBUILT_CONTRIBS_URL

"$cast_jni_root/buildsystem/compile-libvlc.sh" -a arm64 --with-prebuilt-contribs
GRADLE_ABI=ARMv8 ./gradlew -PcastSubtitleLibVlc=true :application:app:assembleDebug
