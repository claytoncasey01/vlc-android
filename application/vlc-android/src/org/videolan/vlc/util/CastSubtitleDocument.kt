/*
 * Copyright © 2026 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package org.videolan.vlc.util

/**
 * A complete external text subtitle document, ready to export for a Cast receiver.
 *
 * This is independent of Android and LibVLC. The caller must decode the source with
 * the selected subtitle encoding and supply the media time corresponding to the
 * receiver stream's zero point. It does not infer that time from the phone's clock.
 */
class CastSubtitleDocument private constructor(private val cues: List<Cue>) {
    enum class Format { SRT, WEBVTT }

    private data class Cue(val startMs: Long, val endMs: Long, val text: String, val settings: String)

    /**
     * Export UTF-8 WebVTT text for a cast stream starting at [streamStartMs].
     * Positive [subtitleDelayMs] displays subtitles later. Cues ending before the
     * new zero point are omitted; cues spanning it are clipped to zero.
     * Export again from the original document when seeking or changing the delay.
     */
    fun toWebVtt(streamStartMs: Long = 0, subtitleDelayMs: Long = 0): String {
        require(streamStartMs >= 0) { "Stream start must be non-negative" }
        val offset = subtractChecked(subtitleDelayMs, streamStartMs)
        return buildString {
            append("WEBVTT\n\n")
            for (cue in cues) {
                val end = addChecked(cue.endMs, offset)
                if (end <= 0) continue
                val start = addChecked(cue.startMs, offset).coerceAtLeast(0)
                append(timestamp(start)).append(" --> ").append(timestamp(end))
                if (cue.settings.isNotEmpty()) append(' ').append(cue.settings)
                append('\n').append(cue.text).append("\n\n")
            }
        }
    }

    companion object {
        private val srtTiming = Regex("^(\\d{2,}:\\d{2}:\\d{2},\\d{3})[ \\t]+-->[ \\t]+(\\d{2,}:\\d{2}:\\d{2},\\d{3})[ \\t]*$")
        private val vttTiming = Regex("^((?:\\d{2,}:)?\\d{2}:\\d{2}\\.\\d{3})[ \\t]+-->[ \\t]+((?:\\d{2,}:)?\\d{2}:\\d{2}\\.\\d{3})(?:[ \\t]+(.*))?$")
        private val srtMarkup = Regex("</?(?:b|i|u)>|&(?:amp|lt|gt|nbsp|lrm|rlm);|[&<>]", RegexOption.IGNORE_CASE)

        /** Parse SRT or basic WebVTT; invalid or unsupported documents fail explicitly. */
        fun parse(source: String, format: Format): CastSubtitleDocument {
            val normalized = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            require('\u0000' !in normalized) { "Subtitle document contains a NUL character" }
            val lines = normalized.split('\n')
            var firstCueLine = 0
            if (format == Format.WEBVTT) {
                val header = lines.first()
                require(header == "WEBVTT" || header.startsWith("WEBVTT ") || header.startsWith("WEBVTT\t")) {
                    "Missing WebVTT header"
                }
                require("-->" !in header) { "Invalid WebVTT header" }
                firstCueLine = 1
                while (firstCueLine < lines.size && lines[firstCueLine].isNotBlank()) {
                    require(!lines[firstCueLine].startsWith("X-TIMESTAMP-MAP")) {
                        "WebVTT timestamp maps require stream-specific handling"
                    }
                    require("-->" !in lines[firstCueLine]) { "Missing blank line after WebVTT header" }
                    firstCueLine++
                }
            }

            val cues = ArrayList<Cue>()
            var cursor = firstCueLine
            while (cursor < lines.size) {
                if (lines[cursor].isBlank()) {
                    cursor++
                    continue
                }
                val blockStart = cursor
                while (cursor < lines.size && lines[cursor].isNotBlank()) cursor++
                val block = lines.subList(blockStart, cursor)
                if (format == Format.WEBVTT) {
                    val first = block.first()
                    if (first == "NOTE" || first.startsWith("NOTE ") || first.startsWith("NOTE\t")) continue
                    require(first != "STYLE" && first != "REGION") {
                        "WebVTT styles and regions are not supported yet"
                    }
                }
                val timingLine = if ("-->" in block.first()) 0 else 1
                require(timingLine < block.size) { "Missing cue timing at line ${blockStart + 1}" }
                if (format == Format.SRT && timingLine == 1) {
                    require(block.first().trim().matches(Regex("\\d+"))) { "Invalid SRT cue number at line ${blockStart + 1}" }
                }
                val timing = (if (format == Format.SRT) srtTiming else vttTiming).matchEntire(block[timingLine])
                requireNotNull(timing) { "Invalid cue timing at line ${blockStart + timingLine + 1}" }
                val start = parseTimestamp(timing.groupValues[1])
                val end = parseTimestamp(timing.groupValues[2])
                require(end > start) { "Cue end must be after its start at line ${blockStart + 1}" }
                require(block.size > timingLine + 1) { "Missing cue text at line ${blockStart + 1}" }
                val rawText = block.drop(timingLine + 1).joinToString("\n")
                require("-->" !in rawText) { "Missing blank line between cues at line ${blockStart + 1}" }
                val text = if (format == Format.SRT) srtMarkup.replace(rawText) {
                    when (it.value) {
                        "&" -> "&amp;"
                        "<" -> "&lt;"
                        ">" -> "&gt;"
                        else -> it.value.lowercase()
                    }
                } else rawText
                val settings = if (format == Format.WEBVTT) timing.groupValues[3].trim() else ""
                require(!Regex("(?:^|\\s)region:").containsMatchIn(settings)) {
                    "WebVTT regions are not supported yet"
                }
                cues.add(Cue(start, end, text, settings))
            }
            require(cues.isNotEmpty()) { "Subtitle document contains no cues" }
            require(cues.zipWithNext().all { (a, b) -> a.startMs <= b.startMs }) {
                "Subtitle cues must be ordered by start time"
            }
            return CastSubtitleDocument(cues)
        }

        private fun parseTimestamp(value: String): Long {
            val parts = value.replace(',', '.').split(':', '.')
            val hours = if (parts.size == 4) parts[0].toLongOrNull() else 0L
            requireNotNull(hours) { "Subtitle timestamp is too large" }
            val minutes = parts[parts.size - 3].toInt()
            val seconds = parts[parts.size - 2].toInt()
            val milliseconds = parts.last().toInt()
            require(minutes < 60 && seconds < 60) { "Invalid subtitle timestamp" }
            val remainder = minutes * 60_000L + seconds * 1_000L + milliseconds
            require(hours <= (Long.MAX_VALUE - remainder) / 3_600_000L) { "Subtitle timestamp is too large" }
            return hours * 3_600_000L + remainder
        }

        private fun timestamp(timeMs: Long): String {
            val hours = timeMs / 3_600_000
            val minutes = timeMs / 60_000 % 60
            val seconds = timeMs / 1_000 % 60
            val milliseconds = timeMs % 1_000
            return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${milliseconds.toString().padStart(3, '0')}"
        }

        // Avoid java.lang.Math.addExact/subtractExact, unavailable on older supported Android APIs.
        private fun addChecked(a: Long, b: Long): Long {
            require(if (b > 0) a <= Long.MAX_VALUE - b else a >= Long.MIN_VALUE - b) {
                "Subtitle timing overflow"
            }
            return a + b
        }

        private fun subtractChecked(a: Long, b: Long): Long {
            require(if (b > 0) a >= Long.MIN_VALUE + b else a <= Long.MAX_VALUE + b) {
                "Subtitle timing overflow"
            }
            return a - b
        }
    }
}
