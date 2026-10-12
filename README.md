VLC Cast Test alpha4: external SRT/basic WebVTT captions in the native Chromecast path.

This build starts from production VLC Android 3.7.1, LibVLC/JNI 3.7.0, and VLC 3.0.23. FFmpeg 4.4.5 matches the production binary's Lavf58.76.100. Its Matroska reader, HEVC packetizer, muxer, and normal casting options are unchanged. The development alpha3 timestamp/demux modifications are not included.

The native subtitle transport serves WebVTT through VLC's HTTP server and selects a separate Cast text track. Caption selection, disabling, and delay reload at the current position. Captions do not force video conversion.

Install as the separate **VLC Cast Test** app. Package: org.videolan.vlc.casttest. Version: 3.7.1 - Cast alpha4. Version code: 13070140. Minimum SDK: 30. Architecture: arm64-v8a. Source branch: feature/chromecast-subtitles-3.7.1 at 85db041.

Stop the previous cast session, open VLC Cast Test, grant media access, and cast the same video to the Chromecast Ultra with captions disabled first. Then select the external SRT. Check playback, seeking, timing and disabling captions. Actual device playback still requires testing.

Native and Android builds, signing verification, exact packaged library checks, 12 subtitle tests and native sanitizer tests passed. The original March contrib archive is no longer hosted; the build pins an official 6 April 2026 snapshot with matching FFmpeg 4.4.5 and Matroska 1.7.0 versions. The APK compresses dex files for distribution while retaining the original native-library packaging mode.

Current limitations: external local text captions and seekable media. Embedded/bitmap subtitles and ASS styling remain unsupported.
