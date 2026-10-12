package org.videolan.vlc.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.videolan.vlc.util.CastSubtitleDocument.Format

class CastSubtitleDocumentTest {
    private val srt = """
        1
        00:00:01,000 --> 00:00:03,000
        First line
        Second line

        2
        00:00:05,000 --> 00:00:07,250
        Later
    """.trimIndent()

    @Test
    fun convertsSrtToWebVttAndPreservesMultilineText() {
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000\nFirst line\nSecond line\n\n" +
                "00:00:05.000 --> 00:00:07.250\nLater\n\n", parseSrt(srt).toWebVtt())
    }

    @Test
    fun acceptsBomAndWindowsOrMacLineEndings() {
        val expected = parseSrt(srt).toWebVtt()
        assertEquals(expected, parseSrt("\uFEFF" + srt.replace("\n", "\r\n")).toWebVtt())
        assertEquals(expected, parseSrt(srt.replace("\n", "\r")).toWebVtt())
    }

    @Test
    fun seekDropsExpiredCuesClipsCurrentCueAndShiftsFutureCues() {
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nFirst line\nSecond line\n\n" +
                "00:00:03.000 --> 00:00:05.250\nLater\n\n", parseSrt(srt).toWebVtt(streamStartMs = 2_000))
        assertEquals("WEBVTT\n\n00:00:02.000 --> 00:00:04.250\nLater\n\n",
                parseSrt(srt).toWebVtt(streamStartMs = 3_000))
        assertEquals("WEBVTT\n\n", parseSrt(srt).toWebVtt(streamStartMs = 8_000))
    }

    @Test
    fun positiveAndNegativeDelaysUseOriginalTimeline() {
        val document = parseSrt(srt)
        assertEquals("WEBVTT\n\n00:00:00.500 --> 00:00:02.750\nLater\n\n",
                document.toWebVtt(streamStartMs = 5_000, subtitleDelayMs = 500))
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.750\nLater\n\n",
                document.toWebVtt(streamStartMs = 5_000, subtitleDelayMs = -500))
        // Exporting after a seek must not mutate the original cues.
        assertEquals(parseSrt(srt).toWebVtt(), document.toWebVtt())
    }

    @Test
    fun webVttPreservesCueSettingsAndMarkupButOmitsCommentsAndIds() {
        val vtt = """
            WEBVTT English

            NOTE This is not a cue
            A second comment line

            caption-id
            00:01.000 --> 00:03.000 align:start position:10%
            <v Speaker><i>Hello</i></v>

            00:05.000 --> 00:07.250
            Later
        """.trimIndent()
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:03.000 align:start position:10%\n" +
                "<v Speaker><i>Hello</i></v>\n\n00:00:05.000 --> 00:00:07.250\nLater\n\n",
                CastSubtitleDocument.parse(vtt, Format.WEBVTT).toWebVtt())
    }

    @Test
    fun srtEscapesLiteralTextWhileRetainingBasicFormattingAndEntities() {
        val source = "1\n00:00:01,000 --> 00:00:02,000\n" +
                "<I>Hello</I> & goodbye <world> &amp; <font color=\"red\">text</font> -->"
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\n" +
                "<i>Hello</i> &amp; goodbye &lt;world&gt; &amp; text --&gt;\n\n",
                parseSrt(source).toWebVtt())
    }

    @Test
    fun preservesUnicodeAndOverlappingCues() {
        val source = "1\n00:00:01,000 --> 00:00:04,000\n你好 👋\n\n" +
                "2\n00:00:02,000 --> 00:00:03,000\nمرحبا"
        assertEquals("WEBVTT\n\n00:00:01.000 --> 00:00:04.000\n你好 👋\n\n" +
                "00:00:02.000 --> 00:00:03.000\nمرحبا\n\n", parseSrt(source).toWebVtt())
    }

    @Test
    fun hourFormattingDoesNotWrapAtTwentyFourHours() {
        assertEquals("WEBVTT\n\n25:00:00.000 --> 25:00:01.000\nLong recording\n\n",
                parseSrt("1\n25:00:00,000 --> 25:00:01,000\nLong recording").toWebVtt())
    }

    @Test
    fun acceptsUnnumberedSrtAndWhitespaceBetweenBlocks() {
        assertEquals(parseSrt(srt).toWebVtt(), parseSrt(srt.replace("1\n", "").replace("2\n", "")
                .replace("\n\n", "\n \t\n")).toWebVtt())
    }

    @Test
    fun rejectsInvalidSrtRatherThanProducingPartialSubtitles() {
        val invalid = listOf(
                "", "1", "1\n00:00:01,000 --> 00:00:02,000",
                "1\n00:60:00,000 --> 01:00:01,000\nInvalid minutes",
                "1\n00:00:60,000 --> 00:01:01,000\nInvalid seconds",
                "1\n00:00:02,000 --> 00:00:01,000\nBackwards",
                "1\n00:00:01,000 --> 00:00:01,000\nZero length",
                "abc\n00:00:01,000 --> 00:00:02,000\nBad number",
                "1\n00:00:01.000 --> 00:00:02.000\nWrong separator",
                srt.replace("\n\n", "\n"), srt + "\n\nInvalid trailing block",
                srt.replace("First line", "\u0000"),
                "1\n00:00:02,000 --> 00:00:04,000\nFirst\n\n2\n00:00:01,000 --> 00:00:03,000\nSecond"
        )
        invalid.forEach { source ->
            assertThrows("Expected invalid source: $source", IllegalArgumentException::class.java) { parseSrt(source) }
        }
    }

    @Test
    fun rejectsUnsupportedWebVttFeaturesAndMalformedHeaders() {
        val cue = "00:01.000 --> 00:02.000\nHello"
        val invalid = listOf(
                cue, "WEBVTTbad\n\n$cue", "WEBVTT -->\n\n$cue", "WEBVTT\n$cue",
                "WEBVTT\nX-TIMESTAMP-MAP=LOCAL:00:00:00.000,MPEGTS:900000\n\n$cue",
                "WEBVTT\n\nSTYLE\n::cue { color: red; }\n\n$cue",
                "WEBVTT\n\nREGION\nid:r1\n\n$cue",
                "WEBVTT\n\n00:01.000 --> 00:02.000 region:r1\nHello"
        )
        invalid.forEach { source ->
            assertThrows(IllegalArgumentException::class.java) { CastSubtitleDocument.parse(source, Format.WEBVTT) }
        }
    }

    @Test
    fun rejectsTimestampAndOffsetOverflowAndNegativeStreamStarts() {
        assertThrows(IllegalArgumentException::class.java) {
            parseSrt("1\n99999999999999999999:00:00,000 --> 99999999999999999999:00:01,000\nOverflow")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseSrt("1\n9223372036854775807:00:00,000 --> 9223372036854775807:00:01,000\nOverflow")
        }
        val document = parseSrt(srt)
        assertThrows(IllegalArgumentException::class.java) { document.toWebVtt(subtitleDelayMs = Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) {
            document.toWebVtt(streamStartMs = 1, subtitleDelayMs = Long.MIN_VALUE)
        }
        assertThrows(IllegalArgumentException::class.java) { document.toWebVtt(streamStartMs = -1) }
    }

    private fun parseSrt(source: String) = CastSubtitleDocument.parse(source, Format.SRT)
}
