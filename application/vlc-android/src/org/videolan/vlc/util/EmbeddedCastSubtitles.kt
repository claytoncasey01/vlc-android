/* Copyright © 2026 VLC authors and VideoLAN. GPL version 2 or later. */
package org.videolan.vlc.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.videolan.libvlc.Media
import org.videolan.resources.VLCInstance
import java.io.File
import java.security.MessageDigest

/** An independent reader: a content URI gets a fresh descriptor, never the playback descriptor. */
object EmbeddedCastSubtitles {
    data class Track(val index: Int, val title: String, val language: String)

    private val discoverMethod by lazy {
        try { Media::class.java.getMethod("getCastSubtitleTracks") } catch (_: Exception) { null }
    }
    private val extractMethod by lazy {
        try { Media::class.java.getMethod("extractCastSubtitle", Int::class.javaPrimitiveType) } catch (_: Exception) { null }
    }

    suspend fun discover(context: Context, uri: Uri): List<Track> = runInterruptible(Dispatchers.IO) {
        val method = discoverMethod ?: return@runInterruptible emptyList()
        val json = read(context, uri) { method.invoke(it) as? String } ?: return@runInterruptible emptyList()
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Track(item.getInt("index"), item.optString("title"), item.optString("language"))
        }
    }

    suspend fun extract(context: Context, uri: Uri, index: Int): CastSubtitleDocument = runInterruptible(Dispatchers.IO) {
        val cached = cacheFile(context, uri, index)
        cached?.takeIf { it.isFile && it.length() in 1..MAX_DOCUMENT_BYTES.toLong() }?.let { file ->
            try {
                val document = CastSubtitleDocument.parse(file.readText(), CastSubtitleDocument.Format.WEBVTT)
                file.setLastModified(System.currentTimeMillis())
                return@runInterruptible document
            } catch (_: Exception) { file.delete() }
        }
        val method = extractMethod ?: throw IllegalStateException("Native embedded subtitles are unavailable")
        val webvtt = read(context, uri) { method.invoke(it, index) as? String }
            ?: throw IllegalArgumentException("Cannot read this embedded subtitle track")
        require(webvtt.toByteArray(Charsets.UTF_8).size <= MAX_DOCUMENT_BYTES)
        val document = CastSubtitleDocument.parse(webvtt, CastSubtitleDocument.Format.WEBVTT)
        if (!Thread.currentThread().isInterrupted && cached != null) {
            try {
                synchronized(this) {
                    cached.parentFile?.mkdirs()
                    val temp = File.createTempFile("subtitle-", ".tmp", cached.parentFile)
                    try { temp.writeText(webvtt); temp.renameTo(cached) } finally { temp.delete() }
                    val files = cached.parentFile?.listFiles()?.filter { it.extension == "vtt" }.orEmpty().sortedBy { it.lastModified() }
                    var size = files.sumOf { it.length() }
                    for (file in files) {
                        if (size <= MAX_CACHE_BYTES) break
                        val length = file.length()
                        if (file.delete()) size -= length
                    }
                }
            } catch (_: Exception) { /* Cache failure must not prevent subtitle playback. */ }
        }
        document
    }

    private fun <T> read(context: Context, uri: Uri, operation: (Media) -> T): T? {
        val instance = VLCInstance.getInstance(context)
        if (uri.scheme == "content") {
            return context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val media = Media(instance, descriptor.fileDescriptor)
                try { operation(media) } finally { media.release() }
            }
        }
        if (uri.scheme != "file") return null
        val media = Media(instance, uri)
        return try { operation(media) } finally { media.release() }
    }

    private fun cacheFile(context: Context, uri: Uri, index: Int): File? {
        // Stable local identity avoids reusing cues after the video is replaced.
        if (uri.scheme != "file") return null
        val source = uri.path?.let(::File) ?: return null
        if (!source.isFile || source.lastModified() <= 0) return null
        val key = "embedded-v1|$uri|${source.length()}|${source.lastModified()}|$index"
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(File(context.cacheDir, "cast-subtitles"), "$hash.vtt")
    }

    private const val MAX_DOCUMENT_BYTES = 4 * 1024 * 1024
    private const val MAX_CACHE_BYTES = 32 * 1024 * 1024L
}
