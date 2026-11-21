package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrOutputPlanTest {
    @Test
    fun mapOffOnRealtekApi28StaysHardwareDirect() {
        val choice = HdrOutputPlan.choose(
            manualOn = false,
            sourceHdr = true,
            vendorGles = true,
            api = 28,
            hardwareRequestAvailable = false,
        )
        assertEquals(HdrMap.DIRECT, choice.mode)
        assertFalse(choice.mapHdrToSdr)
        assertFalse(choice.requestHardwareSdr)
        assertFalse(choice.softwareVideo)
        assertTrue(choice.windowSurface)
    }

    @Test
    fun explicitShaderOnApi28GlesDoesNotForceSoftware() {
        val choice = HdrOutputPlan.choose(
            manualOn = true,
            sourceHdr = true,
            vendorGles = true,
            api = 28,
            hardwareRequestAvailable = false,
        )
        assertEquals("direct-unmapped", choice.mode)
        assertFalse(choice.softwareVideo)
        assertTrue(choice.windowSurface)
    }

    @Test
    fun explicitShaderOffGlesStillUsesSoftware() {
        val choice = HdrOutputPlan.choose(
            manualOn = true,
            sourceHdr = true,
            vendorGles = false,
            api = 28,
            hardwareRequestAvailable = false,
        )
        assertTrue(choice.mapHdrToSdr)
        assertTrue(choice.softwareVideo)
        assertFalse(choice.windowSurface)
    }

    @Test
    fun api31ExplicitMapUsesHardwareRequest() {
        val choice = HdrOutputPlan.choose(
            manualOn = true,
            sourceHdr = true,
            vendorGles = false,
            api = 31,
            hardwareRequestAvailable = true,
        )
        assertEquals(HdrMap.HARDWARE, choice.mode)
        assertTrue(choice.requestHardwareSdr)
        assertFalse(choice.softwareVideo)
        assertFalse(choice.windowSurface)
    }

    @Test
    fun sdrDoesNotOpenHdrWindow() {
        val choice = HdrOutputPlan.choose(
            manualOn = false,
            sourceHdr = false,
            vendorGles = true,
            api = 28,
            hardwareRequestAvailable = false,
        )
        assertEquals(HdrMap.SDR, choice.mode)
        assertFalse(choice.windowSurface)
        assertFalse(choice.softwareVideo)
    }
}
