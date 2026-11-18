package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.ui.player.HdrMap
import com.xxxx.emby_tv.ui.player.PlaybackDebug
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Release266Test {
    @Test
    fun hardwareMapIsPreferredAndShaderIsOnlyTheFallback() {
        assertEquals(
            HdrMap.DIRECT,
            HdrMap.mode(manualOn = false, displayHdr = false, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertEquals(
            HdrMap.HARDWARE,
            HdrMap.mode(manualOn = true, displayHdr = true, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertFalse(
            HdrMap.forceSoftwareDecode(
                manualOn = false, displayHdr = false, sourceHdr = true, api = 31, hardwareRequestAvailable = true,
            ),
        )
        assertEquals(
            HdrMap.DIRECT,
            HdrMap.mode(manualOn = false, displayHdr = false, sourceHdr = true, api = 30, hardwareRequestAvailable = true),
        )
        assertEquals(
            HdrMap.SHADER,
            HdrMap.mode(manualOn = true, displayHdr = true, sourceHdr = true, api = 31, hardwareRequestAvailable = false),
        )
        assertEquals(
            HdrMap.DIRECT,
            HdrMap.mode(manualOn = false, displayHdr = true, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertEquals(HdrMap.SDR, HdrMap.mode(manualOn = true, displayHdr = false, sourceHdr = false, api = 31, hardwareRequestAvailable = true))
        assertEquals(HdrMap.COLOR_TRANSFER_SDR_VIDEO, 3)
        assertEquals(
            "直出 HDR",
            PlaybackDebug.hdrOutput(true, false, api = 31, manualOn = false, hardwareRequestAvailable = true),
        )
        assertEquals(
            "直出 HDR",
            PlaybackDebug.hdrOutput(true, false, api = 31, manualOn = false, hardwareRequestAvailable = false),
        )
        assertEquals(
            "映射 SDR（硬解）",
            PlaybackDebug.hdrOutput(true, false, api = 31, manualOn = true, hardwareRequestAvailable = true),
        )
        assertEquals(
            "直出 HDR",
            PlaybackDebug.hdrOutput(true, true, api = 31, manualOn = false, hardwareRequestAvailable = true),
        )
        assertEquals(
            "直出 SDR",
            PlaybackDebug.hdrOutput(false, false, api = 31, manualOn = true, hardwareRequestAvailable = true),
        )
        assertTrue(
            HdrMap.allowDirectHdr(
                manualOn = true, displayHdr = true, sourceHdr = true, api = 31, hardwareRequestAvailable = true,
            ),
        )
    }

    @Test
    fun detailsHdrChipFollowsTheSelectedSource() {
        val hdr = MediaSourceInfoDto(
            id = "uhd",
            name = "4K",
            mediaStreams = listOf(
                MediaStreamDto(
                    type = "Video",
                    width = 3840,
                    height = 1608,
                    videoRange = "HDR",
                    videoRangeType = "HDR10",
                    colorTransfer = "smpte2084",
                ),
            ),
        )
        val sdr = MediaSourceInfoDto(
            id = "hd",
            name = "1080p",
            mediaStreams = listOf(
                MediaStreamDto(type = "Video", width = 1920, height = 1080, videoRange = "SDR", videoRangeType = "SDR"),
            ),
        )
        val item = BaseItemDto(name = "奥德赛", type = "Movie", mediaSources = listOf(sdr, hdr))
        assertEquals("HDR10", EmbyPresentation.hdrChip(item, hdr))
        assertNull(EmbyPresentation.hdrChip(item, sdr))
        assertEquals("HLG", EmbyPresentation.hdrLabel("HLG", null, "HDR", "arib-std-b67", null))
        assertEquals("杜比视界", EmbyPresentation.hdrLabel("DOVI", null, "HDR", null, null))
        assertEquals("杜比视界", EmbyPresentation.hdrLabel("DOVIWithHDR10", null, "HDR", "smpte2084", null))
        assertNull(EmbyPresentation.hdrLabel(null, null, "SDR", null, "HDR10 HEVC"))
        assertNull(EmbyPresentation.hdrLabel(null, null, null, null, "plain"))
        assertEquals("HDR10", EmbyPresentation.hdrLabel(null, null, null, null, "4K HDR10"))
        assertEquals(1, EmbyPresentation.defaultSourceIndex(listOf(sdr, hdr), preference = 1))
        assertEquals("HDR10", EmbyPresentation.hdrChip(item, listOf(sdr, hdr)[1]))
        assertNull(EmbyPresentation.hdrChip(item, listOf(sdr, hdr)[0]))
        assertFalse(EmbyPresentation.showVersionButton(1))
    }
}
