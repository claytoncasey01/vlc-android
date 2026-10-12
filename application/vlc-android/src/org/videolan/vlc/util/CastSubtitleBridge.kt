/* Copyright © 2026 VLC authors and VideoLAN. GPL version 2 or later. */
package org.videolan.vlc.util

import org.videolan.libvlc.MediaPlayer

/** Keep builds using an unpatched Maven LibVLC compatible with this experiment. */
object CastSubtitleBridge {
    private val setter by lazy {
        try { MediaPlayer::class.java.getMethod("setCastSubtitle", String::class.java) }
        catch (_: Exception) { null }
    }

    val isSupported: Boolean by lazy {
        try { MediaPlayer::class.java.getMethod("supportsCastSubtitles").invoke(null) == true }
        catch (_: Exception) { false }
    }

    fun setDocument(player: MediaPlayer, document: String?): Boolean {
        if (!isSupported || player.isReleased) return false
        return try { setter?.invoke(player, document) == true }
        catch (_: Exception) { false }
    }
}
