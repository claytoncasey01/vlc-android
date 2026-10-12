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
Ultra. Install the production-based alpha4 test APK, open a video, choose the Ultra from
VLC's casting menu, and use the subtitle menu's file picker to select a local SRT
or WebVTT file. The test package is `org.videolan.vlc.casttest`, its launcher name is `VLC Cast Test`, and its displayed version is `3.7.1 - Cast alpha4`. The separate package starts with a fresh media database when moving back from the development-based test builds.

The source base is the official VLC Android 3.7.1 tag, LibVLC/JNI 3.7.0, and VLC 3.0.23, matching the working production app. FFmpeg 4.4.5 retains the original mux timestamp workaround. Alpha1/alpha2 used a newer FFmpeg and dropped reordered HEVC frames; the uploaded APK hash confirmed alpha2 was installed during the latest failing capture. Alpha4 still requires device verification. Check initial
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
and JDK 21 were used for this build. The helper pins an archived official arm64 contrib snapshot from 6 April 2026 because the original March snapshot is no longer hosted; its FFmpeg 4.4.5 and Matroska 1.7.0 versions match the production libraries. No latest contribs are downloaded. Then run:

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

With no selected Cast document, LOAD retains the production message fields and the original initial demux timing path. The Matroska demuxer, HEVC packetizer, and avformat muxer remain unchanged. The alpha3 timestamp/demux patches are not included on this production-based branch.
