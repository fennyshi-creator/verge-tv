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
