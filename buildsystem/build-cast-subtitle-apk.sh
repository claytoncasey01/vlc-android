#!/bin/sh
set -eu

: "${ANDROID_SDK:?Set ANDROID_SDK to an Android SDK with platform 36}"
: "${ANDROID_NDK:?Set ANDROID_NDK to an NDK supported for arm64 (r28c was tested)}"

cast_repo_root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$cast_repo_root"

# Fetch the pinned sources and apply both native and JNI changes.
buildsystem/compile.sh --init -a arm64
cast_jni_root=${VLC_LIBJNI_PATH:-$cast_repo_root/libvlcjni}
cast_contrib_sha=$(cd "$cast_jni_root/vlc" && extras/ci/get-contrib-sha.sh android-arm64)
VLC_PREBUILT_CONTRIBS_URL=${VLC_PREBUILT_CONTRIBS_URL:-https://artifacts.videolan.org/vlc-3.0/android-arm64/vlc-contrib-aarch64-linux-android-$cast_contrib_sha.tar.zst}
export VLC_PREBUILT_CONTRIBS_URL

# Keep the installable test app debuggable, but run the media engine and JNI
# with the release compiler settings used for normal playback builds.
NO_OPTIM=0 "$cast_jni_root/buildsystem/compile-libvlc.sh" -a arm64 --release --with-prebuilt-contribs
GRADLE_ABI=ARMv8 ./gradlew -PcastSubtitleLibVlc=true :application:app:assembleDebug
