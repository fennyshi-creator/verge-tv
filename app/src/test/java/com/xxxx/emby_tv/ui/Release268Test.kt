package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.local.PlaybackMemory
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.player.HdrMap
import com.xxxx.emby_tv.ui.player.PlaybackDebug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release268Test {
    @Test
    fun hdrSwitchOffDoesNotMapEvenOnAnSdrDisplay() {
        assertFalse(HdrMap.needsMap(manualOn = false, displayHdr = false, sourceHdr = true))
        assertTrue(HdrMap.needsMap(manualOn = true, displayHdr = false, sourceHdr = true))
        assertFalse(HdrMap.needsMap(manualOn = false, displayHdr = true, sourceHdr = true))
        assertEquals(
            HdrMap.DIRECT,
            HdrMap.mode(manualOn = false, displayHdr = false, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertEquals(
            HdrMap.HARDWARE,
            HdrMap.mode(manualOn = true, displayHdr = false, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertEquals(
            HdrMap.SHADER,
            HdrMap.mode(manualOn = true, displayHdr = false, sourceHdr = true, api = 30, hardwareRequestAvailable = true),
        )
        assertEquals(
            HdrMap.SHADER,
            HdrMap.mode(manualOn = true, displayHdr = false, sourceHdr = true, api = 31, hardwareRequestAvailable = false),
        )
        assertEquals(
            HdrMap.DIRECT,
            HdrMap.mode(manualOn = false, displayHdr = true, sourceHdr = true, api = 31, hardwareRequestAvailable = true),
        )
        assertEquals(
            "直出 HDR",
            PlaybackDebug.hdrOutput(true, false, api = 31, manualOn = false, hardwareRequestAvailable = true),
        )
        assertEquals(
            "映射 SDR（硬解）",
            PlaybackDebug.hdrOutput(true, false, api = 31, manualOn = true, hardwareRequestAvailable = true),
        )
        assertEquals(
            "映射 SDR（Vulkan 着色器）",
            PlaybackDebug.hdrOutput(true, false, api = 30, manualOn = true, hardwareRequestAvailable = true),
        )
        assertFalse(PlaybackMemory.Session().hdrToSdr)
    }

    @Test
    fun firstScreenDarkensUnderThePosterRowAndIsOpaqueAtTheBottom() {
        assertFalse(HomeMask.horizontalHeroCutoff())
        assertFalse(HomeMask.hardBlackSlab())
        assertTrue(HomeMask.CLEAR_UNTIL < 0.70f)
        assertEquals(0f, HomeMask.firstScreenDarken(0.45f))
        assertTrue(HomeMask.firstScreenDarken(0.66f) > 0f)
        assertTrue(HomeMask.firstScreenDarken(0.78f) > 0.45f)
        assertEquals(1f, HomeMask.firstScreenDarken(1f))
        assertTrue(HomeMask.stillCleared(1080, 1080))
        assertFalse(HomeMask.stillCleared(200, 1080))
        assertEquals(HomeStyle.background, HomeMask.scrimColor(androidx.compose.ui.graphics.Color(0xFFB7D9F2)))
        assertEquals(0f, HomeMask.shiftDp("continue"))
    }
}
