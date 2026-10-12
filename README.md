Experimental arm64 Android test build with native Chromecast support for external SRT/basic WebVTT subtitles.

Install the APK on the Pixel, open a video, choose the Chromecast Ultra from VLC's casting menu, then select a local subtitle file from VLC's subtitle menu. Use VLC's cast controls rather than Pixel screen mirroring.

The debug package is `org.videolan.vlc.debug`, separate from the Play Store app. Native libraries and the APK built successfully; the APK signature and included native API symbols were verified. Physical receiver playback and timing have not been tested yet.

Please test initial playback, forward/backward seeks, subtitle delay, disabling subtitles, switching files, and reconnecting. This initial build supports external text subtitles and seekable media; embedded/bitmap subtitles and ASS styling are not included.

APK SHA256: `d1ac1b73565cb6dc4586c2c896818b400bd1f58905b8f23c3ac771c06d68969b`.
