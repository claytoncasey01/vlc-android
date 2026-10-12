# External subtitles for Chromecast

## Status

The first milestone is a pure Kotlin component that parses external SRT and basic
WebVTT documents and exports a complete WebVTT document. It supports multiline and
Unicode text, basic SRT bold/italic/underline formatting, WebVTT cue settings, and
timing adjustments for a cast stream starting partway through a video. SRT markup
outside the supported tags is escaped as literal text. WebVTT STYLE, REGION, and
X-TIMESTAMP-MAP are rejected explicitly rather than silently changing their meaning.

**Playback integration is not implemented. This branch does not yet display
subtitles on a Chromecast or enable the casting subtitle controls.**

The initial target is VLC's direct Google Cast connection to a Chromecast Ultra.
Phone screen mirroring is a separate path. This implementation does not force
video transcoding; the intended receiver will render a separate text track.

## Using the component

`CastSubtitleDocument` lives in `org.videolan.vlc.util`. Read the complete subtitle
file using the user's selected text encoding before parsing it. Encode the
exported string as UTF-8 when serving it with `Content-Type: text/vtt`.

```kotlin
val document = CastSubtitleDocument.parse(text, CastSubtitleDocument.Format.SRT)
val webVtt = document.toWebVtt(streamStartMs = 120_000, subtitleDelayMs = 500)
```

Here the receiver's stream zero corresponds to media time 120 seconds; subtitles
are delayed by 500 milliseconds. Expired cues are removed and cues spanning zero
are clipped. Always export from the original document after a seek or delay
change. The caller must obtain the actual stream time origin from the native
pipeline; the requested seek position alone may differ from the first delivered
video timestamp. Converting LibVLC delay values also requires microseconds to
milliseconds conversion at the integration boundary.

## Remaining integration

Android's default build uses LibVLC 3. The Android build pins libvlcjni, whose build
in turn pins native VLC. Kotlin-only changes cannot complete this feature.

1. In native VLC's `modules/stream_out/chromecast`, add external text-track
   metadata to the LOAD message (`tracks`, `trackContentId`, `trackContentType`,
   language, and track IDs) and implement selection/disable using the Cast media
   protocol. VLC already uses the Default Media Receiver.
2. Serve a complete WebVTT document from the existing native HTTP server with
   appropriate CORS headers. Give each cast session/export a fresh URL so a
   receiver cannot reuse subtitles from a previous video or seek. Bound input
   size and remove resources when the cast session ends.
3. Connect document selection and timing to the native pipeline. Test the actual
   receiver timeline across seeks and stream reloads before assuming that the
   requested media position is the stream's zero point. Re-export/reload when
   the native pipeline requires it, and handle receiver errors explicitly.
4. Expose the required bridge through libvlcjni, build a custom LibVLC AAR, and
   wire external-file selection in Android. Replace the blanket `hasRenderer()`
   subtitle restriction in `VideoTracksDialog` with a supported-capability check.
5. Test on the Pixel and Chromecast Ultra: cast before/after starting playback,
   start at a saved position, seek both directions, change subtitle delay, toggle
   subtitles, switch files, move to the next video, and disconnect/reconnect.
   Include 1080p/4K content and check that enabling text subtitles does not itself
   force video re-encoding. Direct LG TV casting needs its receiver protocol
   identified and its behavior tested separately.

Embedded subtitles, styled ASS, bitmap subtitles, and a burn-in fallback are
outside this first milestone.

## Validation

The JUnit tests live in the application's existing `test` source set. They can
also run independently of the Android SDK using an unpacked Kotlin compiler,
JUnit 4.13.2, and Hamcrest Core 1.3:

```sh
CAST_KOTLINC=/path/to/kotlinc/bin/kotlinc \
CAST_JUNIT_JAR=/path/to/junit-4.13.2.jar \
CAST_HAMCREST_JAR=/path/to/hamcrest-core-1.3.jar \
bash buildsystem/check-cast-subtitles.sh
```

Use the Kotlin version specified in the root `build.gradle` for the full project
build. Standalone tests exercise parsing and timestamp conversion; they do not
validate APK compilation, native integration, or a physical Cast receiver.

## References

- [Google Cast supported media and subtitle formats](https://developers.google.com/cast/docs/media)
- [Cast text-track metadata](https://developers.google.com/cast/docs/reference/web_receiver/cast.framework.messages.Track)
- [VLC's subtitle casting engineering issue](https://code.videolan.org/videolan/vlc/-/work_items/19310)
