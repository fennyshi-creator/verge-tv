package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DolbyPassthroughTest {
    @Test
    fun mapsAc3AndEac3AndJoc() {
        assertEquals(
            DolbyPassthrough.ENCODING_AC3,
            DolbyPassthrough.encodingFor("ac3", joc = false, enabled = true, speedIsOne = true).encoding,
        )
        assertEquals(
            DolbyPassthrough.ENCODING_E_AC3,
            DolbyPassthrough.encodingFor("eac3", joc = false, enabled = true, speedIsOne = true).encoding,
        )
        val joc = DolbyPassthrough.encodingFor("eac3", joc = true, enabled = true, speedIsOne = true)
        assertEquals(DolbyPassthrough.ENCODING_E_AC3_JOC, joc.encoding)
        assertTrue(joc.joc)
    }

    @Test
    fun truehdAndDisabledAndSpeedStayPcm() {
        assertEquals(0, DolbyPassthrough.encodingFor("truehd", joc = true, enabled = true, speedIsOne = true).encoding)
        assertEquals(0, DolbyPassthrough.encodingFor("eac3", joc = true, enabled = false, speedIsOne = true).encoding)
        assertEquals(0, DolbyPassthrough.encodingFor("eac3", joc = true, enabled = true, speedIsOne = false).encoding)
        assertFalse(
            DolbyPassthrough.encodingFor("truehd", joc = false, enabled = true, speedIsOne = true, endpointOpenOk = 0).fallbackPcm,
        )
    }

    @Test
    fun unsupportedEndpointFallsBackToPcm() {
        val open = DolbyPassthrough.encodingFor(
            "eac3",
            joc = true,
            enabled = true,
            speedIsOne = true,
            endpointOpenOk = 0,
        )
        assertEquals(0, open.encoding)
        assertTrue(open.fallbackPcm)
        assertFalse(open.joc)

        val kept = DolbyPassthrough.encodingFor(
            "ac3",
            joc = false,
            enabled = true,
            speedIsOne = true,
            endpointOpenOk = 1,
        )
        assertEquals(DolbyPassthrough.ENCODING_AC3, kept.encoding)
        assertFalse(kept.fallbackPcm)
    }

    @Test
    fun jocComesFromAtmosMetadataNotFromTrueHd() {
        val atmos = MediaStreamDto(codec = "eac3", profile = "Atmos", displayTitle = "Dolby Digital+")
        val plain = MediaStreamDto(codec = "eac3", displayTitle = "Dolby Digital+")
        val truehd = MediaStreamDto(codec = "truehd", displayTitle = "Dolby TrueHD Atmos")
        assertTrue(DolbyPassthrough.wantsJoc(atmos))
        assertFalse(DolbyPassthrough.wantsJoc(plain))
        assertFalse(DolbyPassthrough.wantsJoc(truehd))
    }

    @Test
    fun statusLineNamesTheOutputMode() {
        val eac3 = MediaStreamDto(codec = "eac3")
        val joc = MediaStreamDto(codec = "eac3", title = "Atmos")
        val aac = MediaStreamDto(codec = "aac")
        assertEquals(
            "「E-AC-3 · 透传」",
            DolbyPassthrough.statusLine(eac3, DolbyPassthrough.ENCODING_E_AC3, fellBackToPcm = false),
        )
        assertEquals(
            "「E-AC-3 JOC · 透传」",
            DolbyPassthrough.statusLine(joc, DolbyPassthrough.ENCODING_E_AC3_JOC, fellBackToPcm = false),
        )
        assertEquals(
            "「E-AC-3 · 解码」",
            DolbyPassthrough.statusLine(eac3, openedEncoding = 0, fellBackToPcm = true),
        )
        assertEquals(
            "「AAC · 解码」",
            DolbyPassthrough.statusLine(aac, openedEncoding = 0, fellBackToPcm = false),
        )
        assertEquals(
            "「AAC 5.1 · 解码」",
            DolbyPassthrough.statusLine(
                MediaStreamDto(codec = "aac", channels = 6),
                openedEncoding = 0,
                fellBackToPcm = false,
            ),
        )
        assertEquals(
            "「E-AC-3 · 透传」",
            DolbyPassthrough.statusLine(
                MediaStreamDto(codec = "eac3", channels = 6),
                DolbyPassthrough.ENCODING_E_AC3,
                fellBackToPcm = false,
            ),
        )
    }
}

class EmbySubtitlesTest {
    @Test
    fun textTrackGetsStreamSrtWithoutExternalFlag() {
        val track = MediaStreamDto(
            codec = "subrip",
            type = "Subtitle",
            index = 3,
            isExternal = false,
            isTextSubtitleStream = true,
            supportsExternalStream = true,
        )
        assertTrue(EmbySubtitles.shouldDeliverAsText(track))
        assertEquals(
            "https://emby.example/emby/Videos/item/source/Subtitles/3/Stream.srt?api_key=token",
            EmbySubtitles.streamUrl(
                serverUrl = "https://emby.example",
                mediaId = "item",
                mediaSourceId = "source",
                apiKey = "token",
                track = track,
            ),
        )
    }

    @Test
    fun vttUsesStreamVttAndOffDoesNotLoad() {
        val vtt = MediaStreamDto(codec = "webvtt", type = "Subtitle", index = 2, supportsExternalStream = true)
        assertTrue(
            EmbySubtitles.streamUrl("https://h", "m", "s", "k", vtt)!!.contains("Stream.vtt"),
        )
        val video = MediaStreamDto(codec = "hevc", type = "Video", index = 0, supportsExternalStream = true)
        assertEquals(null, EmbySubtitles.streamUrl("https://h", "m", "s", "k", video))
    }

    @Test
    fun srtCueMatchesPlaybackTime() {
        val body = """
            1
            00:00:01,000 --> 00:00:04,000
            你好

            2
            00:00:05.500 --> 00:00:06.000
            later
        """.trimIndent()
        val cues = SubtitleCues.parse(body)
        assertEquals(2, cues.size)
        assertEquals("你好", SubtitleCues.textAt(cues, 1500))
        assertEquals(null, SubtitleCues.textAt(cues, 4500))
        assertEquals("later", SubtitleCues.textAt(cues, 5600))
    }
}

class EmbyPlaybackTest {
    @Test
    fun directPlayKeepsHlsAndCopiesDolbyBeforeAac() {
        assertTrue(EmbyPlayback.DIRECT_PLAY_CONTAINERS.split(',').contains("hls"))
        assertTrue(EmbyPlayback.DIRECT_PLAY_CONTAINERS.split(',').contains("m3u8"))
        assertTrue(EmbyPlayback.HLS_TRANSCODE_AUDIO.startsWith("eac3,ac3"))
        val codecs = EmbyPlayback.ensureDolbyAudioCodecs("aac,mp3")
        assertTrue(codecs.split(',').contains("eac3"))
        assertTrue(codecs.split(',').contains("ac3"))
    }

    @Test
    fun prefersDirectStreamAndPublishedHlsOverTranscode() {
        val direct = MediaSourceInfoDto(
            directStreamUrl = "/videos/1/stream.m3u8",
            transcodingUrl = "/videos/1/master.m3u8?AudioCodec=aac",
        )
        assertEquals(
            "https://emby.example/emby/videos/1/stream.m3u8",
            EmbyPlayback.resolvePlaybackUrl("https://emby.example", direct, forceTranscode = false),
        )
        val published = MediaSourceInfoDto(
            container = "hls",
            path = "https://cdn.example/show/index.m3u8",
            transcodingUrl = "/videos/1/master.m3u8",
        )
        assertEquals(
            "https://cdn.example/show/index.m3u8",
            EmbyPlayback.resolvePlaybackUrl("https://emby.example", published, forceTranscode = false),
        )
        assertEquals(
            "https://emby.example/emby/videos/1/master.m3u8",
            EmbyPlayback.resolvePlaybackUrl("https://emby.example", published, forceTranscode = true),
        )
    }
}
