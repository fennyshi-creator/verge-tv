package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.local.PlaybackMemory
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.UserDataDto
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.player.AudioPolicy
import com.xxxx.emby_tv.ui.player.DolbyPassthrough
import com.xxxx.emby_tv.ui.player.HdrMap
import com.xxxx.emby_tv.ui.player.PassthroughRoute
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release265Test {
    private fun source(id: String, name: String, height: Int, codec: String, channels: Int, dv: Boolean = false): MediaSourceInfoDto {
        return MediaSourceInfoDto(
            id = id,
            name = name,
            runTimeTicks = 90L * 60L * 10_000_000L,
            mediaStreams = listOf(
                MediaStreamDto(
                    type = "Video",
                    width = if (height >= 1600) 3840 else 1920,
                    height = height,
                    videoRange = if (dv) "DV" else "SDR",
                    videoRangeType = if (dv) "DOVI" else null,
                ),
                MediaStreamDto(type = "Audio", codec = codec, channels = channels),
            ),
        )
    }

    @Test
    fun versionSelectUpdatesFactsAndDoesNotPlay() {
        val hd = source("hd", "1080p", 1080, "aac", 6)
        val uhd = source("uhd", "4K", 2160, "eac3", 6, dv = true)
        val sources = listOf(hd, uhd)
        val index = EmbyPresentation.defaultSourceIndex(sources, preference = 0)
        assertEquals(1, index)
        val chosen = sources[index]
        val item = BaseItemDto(
            name = "奥德赛",
            type = "Movie",
            runTimeTicks = 2L * 60L * 60L * 10_000_000L,
            userData = UserDataDto(playbackPositionTicks = 30L * 60L * 10_000_000L),
            mediaSources = sources,
        )
        val facts = EmbyPresentation.sourceFacts(item, chosen)
        assertEquals("4K", facts.second)
        assertEquals("4K", EmbyPresentation.versionButtonLabel(chosen))
        assertEquals("DD+ 5.1", facts.third)
        assertEquals("1h 30min", facts.first)
        assertFalse(EmbyPresentation.versionConfirmStartsPlayback())
        assertFalse(EmbyPresentation.versionMenuAfterConfirm(menuOpen = true))
        assertTrue(EmbyPresentation.versionMenuAfterConfirm(menuOpen = false))
        assertEquals(30L * 60L * 10_000_000L, EmbyPresentation.resumePositionTicks(item))
        val plain = EmbyPresentation.defaultSourceIndex(sources, preference = 3)
        assertEquals(0, plain)
        assertEquals("1080P", EmbyPresentation.resolutionChip(sources[plain]))
        assertEquals("1080P", EmbyPresentation.versionButtonLabel(sources[plain]))
        assertFalse(EmbyPresentation.showVersionButton(1))
    }

    @Test
    fun autoUsesFfmpegStereoWhenPassthroughIsUnsafe() {
        val eac3 = setOf(DolbyPassthrough.ENCODING_E_AC3)
        assertFalse(
            PassthroughRoute.allowBitstream(
                PassthroughRoute.TYPE_HDMI,
                eac3,
                directQuery = true,
                maxChannels = 2,
            ),
        )
        assertTrue(
            PassthroughRoute.allowBitstream(
                PassthroughRoute.TYPE_HDMI,
                eac3,
                directQuery = false,
                maxChannels = 6,
            ),
        )
        assertTrue(
            PassthroughRoute.allowBitstream(
                PassthroughRoute.TYPE_HDMI_ARC,
                eac3,
                directQuery = false,
                maxChannels = 2,
            ),
        )
        assertTrue(
            PassthroughRoute.allowBitstream(
                PassthroughRoute.TYPE_HDMI,
                emptySet(),
                directQuery = false,
                maxChannels = 2,
            ),
        )
        assertFalse(
            AudioPolicy.usePassthrough(
                decoderPreference = AudioPolicy.AUTO,
                forceStereo = false,
                speedIsOne = true,
                recoveryStage = 0,
                routeAllows = false,
            ),
        )
        assertFalse(
            AudioPolicy.usePassthrough(
                decoderPreference = AudioPolicy.FFMPEG,
                forceStereo = false,
                speedIsOne = true,
                recoveryStage = 0,
                routeAllows = true,
            ),
        )
        assertTrue(
            AudioPolicy.usePassthrough(
                decoderPreference = AudioPolicy.AUTO,
                forceStereo = false,
                speedIsOne = true,
                recoveryStage = 0,
                routeAllows = true,
            ),
        )
        assertFalse(
            AudioPolicy.usePassthrough(
                decoderPreference = AudioPolicy.AUTO,
                forceStereo = true,
                speedIsOne = true,
                recoveryStage = 0,
                routeAllows = true,
            ),
        )
        assertEquals(2, PassthroughRoute.maxChannelCount(emptyList(), listOf(2)))
        assertEquals(-1, PassthroughRoute.maxChannelCount(emptyList(), emptyList()))
    }

    @Test
    fun hdrSwitchPersistsAndOnRequestsTheShader() {
        assertFalse(HdrMap.forceSoftwareDecode(manualOn = false, displayHdr = true, sourceHdr = true))
        assertTrue(HdrMap.forceSoftwareDecode(manualOn = true, displayHdr = true, sourceHdr = true))
        assertFalse(HdrMap.forceSoftwareDecode(manualOn = false, displayHdr = false, sourceHdr = true))
        assertFalse(HdrMap.forceSoftwareDecode(manualOn = true, displayHdr = false, sourceHdr = false))
        assertEquals(HdrMap.SHADER, HdrMap.output(manualOn = true, displayHdr = true, sourceHdr = true))
        assertEquals(HdrMap.DIRECT, HdrMap.output(manualOn = false, displayHdr = true, sourceHdr = true))
        assertEquals(HdrMap.DIRECT, HdrMap.output(manualOn = false, displayHdr = false, sourceHdr = true))
        assertFalse(HdrMap.allowDirectHdr(manualOn = true))
        val first = PlaybackMemory.Session()
        first.hdrToSdr = true
        first.decodeToStereo = true
        first.decoderPreference = PlaybackMemory.DECODER_FFMPEG
        first.playbackCorrection = 1
        first.playMode = 2
        val next = first.restore()
        assertTrue(next.hdrToSdr)
        assertTrue(next.decodeToStereo)
        assertEquals(PlaybackMemory.DECODER_FFMPEG, next.decoderPreference)
        assertEquals(1, next.playbackCorrection)
        assertEquals(2, next.playMode)
        assertFalse(PlaybackMemory.Session().hdrToSdr)
    }

    @Test
    fun firstScreenStillFadesWithoutASlabAndLeavesOnTheNextScreen() {
        assertTrue(HomeMask.coversFirstScreen())
        assertFalse(HomeMask.hardBlackSlab())
        assertEquals(0f, HomeMask.firstScreenDarken(0.2f))
        assertEquals(0f, HomeMask.firstScreenDarken(0.55f))
        assertTrue(HomeMask.firstScreenDarken(0.82f) > HomeMask.firstScreenDarken(0.74f))
        assertTrue(HomeMask.firstScreenDarken(1f) > 0.7f)
        assertTrue(HomeMask.stillCleared(scrollPx = 1080, viewportPx = 1080))
        assertFalse(HomeMask.stillCleared(scrollPx = 200, viewportPx = 1080))
        assertEquals(0f, HomeMask.shiftDp("continue"))
    }
}
