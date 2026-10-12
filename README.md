Experimental arm64 Android build for native Chromecast external SRT/basic WebVTT subtitles.

Alpha3 addresses Matroska/HEVC frame rejection with modern FFmpeg. Chromecast Matroska streams use the avformat reader for codec-derived decode timestamps. Missing initial DTS are derived from the parsed reorder delay, retained in stream ticks, and clipped to VLC 3 time zero without shifting PTS. Decode clock tracking resets after seeks. Native frame-write errors now include the FFmpeg error reason and stream index.

An actual reordered HEVC sample reproduces frame rejection with legacy MKV DTS estimates. The production initial-DTS helper plus avformat timestamps writes all 150 frames without errors; decoded pixel hashes match the source. Seeking writes all 86 remaining frames without errors. The patched native arm64 release engine and Android debug APK built successfully. Actual Pixel/Chromecast Ultra playback and caption synchronization need retesting.

Install over the previous test APK. Play the same video and use VLC's Cast button to connect to the Ultra. Check video with captions disabled first, then select the external SRT. Test seeking, timing, delay, and disabling captions.

Package: org.videolan.vlc.debug. Source: feature/chromecast-text-subtitles at c6824ab. Engine and JNI use release build settings. Embedded/bitmap subtitles and ASS styling remain unsupported.

APK SHA256: `8d99bab5343dcbfcff62b31438a0fc72baa06d37a840d70715769cef18fd11e4`.
