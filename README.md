VLC Cast Test alpha5 adds embedded text subtitles to the working production-based Chromecast build.

Install VLC-cast-subtitles-3.7.1-arm64-alpha5.apk with adb install -r. It upgrades alpha4 under the same package, org.videolan.vlc.casttest, and signing certificate. Displayed version: 3.7.1 - Cast alpha5. Version code: 13070150. Minimum Android SDK: 30. Architecture: arm64-v8a.

Open the original local video in VLC Cast Test, cast to the Chromecast Ultra, open Subtitles and select its embedded track. No manual extraction or sidecar file is required. The first selection shows Preparing subtitles while conversion runs; large MKVs may require a full file scan. Converted file-URI tracks are cached privately (32 MB limit, file size/mtime invalidation). Content URIs use an independent descriptor and reuse prepared tracks during the current playback. Changing tracks, stopping or disconnecting cancels obsolete conversion.

Supported embedded formats: SRT/plain text, ASS/SSA, MP4 timed text and basic WebVTT. ASS positioning and styling are removed and vector drawings omitted. Bitmap subtitles (PGS/DVD/VobSub) remain unsupported. Extraction requires local, seekable media; each converted document is limited to 4 MB/100,000 cues and conversion has a two-minute deadline. External SRT/WebVTT selection still works.

Source branch: feature/chromecast-embedded-subtitles at ae8685b, based on merged cast-base/vlc-3.7.1. The native video demuxer, muxer, packetizers, Chromecast path and casting options are unchanged from alpha4. Native extraction is an isolated FFmpeg 4.4.5 reader; it discards A/V streams and never decodes or transcodes them. The VLC Android 3.7.1 / LibVLC 3.7.0 / VLC 3.0.23 baseline and archived official 6 April 2026 contrib snapshot remain the same as alpha4.

Validation: native and Android arm64 builds, signature/manifest verification, exact packaged native-library checks, reproducible native/JNI patch application, 12 caption document tests, and embedded ASS/SRT/MP4 extraction fixtures with address/undefined-behavior sanitizers using FFmpeg 4.4.5 passed. Fixtures cover Unicode, metadata, track selection, timing/rebasing and cancellation. Alpha4 casting was confirmed working on the user's Chromecast Ultra; alpha5 still needs device playback verification.

Test the original video with subtitles disabled, then select an embedded track, seek in both directions, adjust subtitle delay, switch tracks and disable subtitles. Check the displayed version is Cast alpha5.
