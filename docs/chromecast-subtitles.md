# Chromecast subtitles experiment

This branch implements external SRT/basic WebVTT and embedded text subtitles through VLC's native
Google Cast path. Android prepares the selected track and sends a complete UTF-8
document through a new LibVLC/JNI API. VLC serves it from its existing HTTP server
with CORS headers, advertises a selected Cast text track, and exports a fresh URL
with rebased cue times when the cast stream reloads after a seek. Selecting,
disabling, or changing the subtitle delay reloads the stream at the current media
position. Text subtitles do not themselves require video transcoding.

## Trying the build

The initial target is an arm64 Pixel casting directly from VLC to a Chromecast
Ultra. Install the production-based alpha5 test APK, open a local video, choose the Ultra from
VLC's casting menu, and select an embedded subtitle track from the subtitle menu.
The first selection shows “Preparing subtitles…” while the native reader converts the track.
External SRT or WebVTT files can still be selected with the file picker.
The test package is `org.videolan.vlc.casttest`, its launcher name is `VLC Cast Test`, and its displayed version is `3.7.1 - Cast alpha5`. It upgrades alpha4 in place; stock VLC and the earlier development-based test package remain separate.

The source base is the official VLC Android 3.7.1 tag, LibVLC/JNI 3.7.0, and VLC 3.0.23, matching the working production app. FFmpeg 4.4.5 retains the original mux timestamp workaround. Alpha4 casting was confirmed working on the user's Chromecast Ultra. Alpha5's embedded subtitles still require device verification. Check initial
playback, seeking in both directions, subtitle delay, disabling subtitles,
switching files, and reconnecting. Direct LG casting needs its receiver protocol
and behavior checked separately; phone screen mirroring uses a different path.

Embedded SRT/plain text, ASS/SSA, MP4 timed text and basic WebVTT are supported.
ASS positioning and styling are removed; vector drawing commands are omitted.
Bitmap subtitles (PGS, VobSub/DVD), WebVTT STYLE/REGION and
timestamp maps are outside the scope. SRT bold/italic/underline are
preserved; font wrappers are stripped and other unsupported tags are escaped.
Changing subtitles during casting currently requires seekable media.

## Embedded track preparation

An isolated native extractor opens a separate local reader, probes the container's
subtitle streams, and returns their stream indexes, titles and languages. Selecting
a track demuxes its packets into a bounded WebVTT document. Audio and video streams
are discarded and never decoded or transcoded by this reader. Matroska may still
need a full file scan, so the first conversion can take time on a large movie.
Conversion runs on an interruptible worker with a two-minute limit; switching tracks,
changing the video, stopping playback or disconnecting cancels obsolete work.

The app caches converted tracks from file URIs in its private cache directory,
keyed by URI, file size, modification time and container stream index. It evicts
the oldest entries above 32 MB. Content URIs get a fresh descriptor for every read;
these currently reuse converted documents during the current playback only.
Network sources and non-seekable inputs are not extracted. A document is limited
to 4 MB and 100,000 cues. Conversion failures leave the previously active track
in place and show an error instead of selecting a partial document.

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

`buildsystem/check-embedded-cast-subtitles.sh` creates MKV/MP4 fixtures and checks
ASS/SRT/MP4 extraction, Unicode, track isolation, timing and cancellation with
address/undefined-behavior sanitizers. Set `CAST_VLC_ROOT` to the patched VLC
sources and `CAST_FFMPEG_ROOT` to a host build of FFmpeg 4.4.5; fixture generation
also needs a host `ffmpeg` executable.

With no selected Cast document, LOAD retains the production message fields and the original initial demux timing path. The Matroska demuxer, HEVC packetizer, and avformat muxer remain unchanged. The alpha3 timestamp/demux patches are not included on this production-based branch.
