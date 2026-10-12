# Chromecast subtitles experiment

This branch implements external SRT/basic WebVTT subtitles through VLC's native
Google Cast path. Android parses the selected file and sends a complete UTF-8
document through a new LibVLC/JNI API. VLC serves it from its existing HTTP server
with CORS headers, advertises a selected Cast text track, and exports a fresh URL
with rebased cue times when the cast stream reloads after a seek. Selecting,
disabling, or changing the subtitle delay reloads the stream at the current media
position. Text subtitles do not themselves require video transcoding.

## Trying the build

The initial target is an arm64 Pixel casting directly from VLC to a Chromecast
Ultra. Install the experimental debug APK, open a video, choose the Ultra from
VLC's casting menu, and use the subtitle menu's file picker to select a local SRT
or WebVTT file. The debug app's package is `org.videolan.vlc.debug`.

This is an experimental build, not a device-verified release. Check initial
playback, seeking in both directions, subtitle delay, disabling subtitles,
switching files, and reconnecting. Direct LG casting needs its receiver protocol
and behavior checked separately; phone screen mirroring uses a different path.

Embedded subtitles, ASS styling, bitmap subtitles, and WebVTT STYLE/REGION or
timestamp maps are outside the initial scope. SRT bold/italic/underline are
preserved; font wrappers are stripped and other unsupported tags are escaped.
Changing subtitles during casting currently requires seekable media.

## Building

Set up the [normal VLC Android build dependencies](https://wiki.videolan.org/AndroidCompile/),
an Android SDK with platform 36, a full JDK, and an arm64-compatible NDK. NDK r28c
and JDK 21 were used for this build. Then run:

```sh
ANDROID_SDK=/path/to/sdk ANDROID_NDK=/path/to/ndk \
    buildsystem/build-cast-subtitle-apk.sh
```

The build applies the patches in `buildsystem/patches/vlc` and
`buildsystem/patches/libvlcjni` without resetting modified sources. The
`castSubtitleLibVlc` Gradle property substitutes the patched local LibVLC for all
Maven LibVLC dependencies, so the APK cannot mix the old and patched engines.
Ordinary builds using the published engine retain the casting subtitle restriction.

The native timestamp/track tests are part of VLC's test sources. The Android
document tests are in the existing JUnit source set and can also be run with
`buildsystem/check-cast-subtitles.sh` without an Android SDK.
