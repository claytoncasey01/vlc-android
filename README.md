Experimental arm64 Android build for native Chromecast external SRT/basic WebVTT subtitles.

Alpha2 rebuilds the same subtitle implementation with release native engine/JNI settings: compiler optimization at -O3 in VLC, runtime assertions disabled, and release JNI. The Android app stays debuggable and uses the same package/signing key as alpha1, allowing an in-place update.

Hardware testing confirms external subtitles appear on the Chromecast Ultra. Alpha1 video playback was choppy with captions both enabled and disabled, while the same video cast smoothly from Play Store VLC. Alpha2 is a diagnostic comparison; the performance fix is not yet confirmed. Playback logs and hardware retesting are needed.

Install on the Pixel, open the same video, and use VLC's Cast button to connect to the Ultra. Check playback with subtitles disabled first, then select the external SRT from the subtitle file picker. Test seeking, synchronization, delay, disabling, and reconnecting.

Package: org.videolan.vlc.debug. Source: feature/chromecast-text-subtitles at 141c766. Embedded/bitmap subtitles and ASS styling remain unsupported.

APK SHA256: `5d446a71101563059988d20eda72e210fe354fc5198d49c3c296aabab472a3b3`.
