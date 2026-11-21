package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AacAc3PolicyTest {
    private val profile = AacAc3Policy.isVerifiedProfile("Realtek", "thor", "mjolnir", "tv794a")

    @Test
    fun verifiedProfileIsNarrow() {
        assertTrue(profile)
        assertFalse(AacAc3Policy.isVerifiedProfile("Realtek", "exynos", "universal", "Phone"))
        assertFalse(AacAc3Policy.isVerifiedProfile("Sony", "thor", "mjolnir", "tv794a"))
        assertFalse(AacAc3Policy.isVerifiedProfile("Dune HD", "rtd1619", "rtd", "Solo"))
    }

    @Test
    fun autoEnablesOnlyVerifiedHdmiAac() {
        assertTrue(enable(AacAc3Policy.AUTO, profile = true, codec = "aac", channels = 6, route = true))
        assertFalse(enable(AacAc3Policy.AUTO, profile = false, codec = "aac", channels = 6, route = true))
        assertFalse(enable(AacAc3Policy.AUTO, profile = true, codec = "aac", channels = 6, route = false))
        assertFalse(enable(AacAc3Policy.AUTO, profile = true, codec = "eac3", channels = 6, route = true))
        assertFalse(enable(AacAc3Policy.AUTO, profile = true, codec = "ac3", channels = 6, route = true))
        assertFalse(enable(AacAc3Policy.AUTO, profile = true, codec = "aac", channels = 2, route = true))
    }

    @Test
    fun onOffAndStereoAndSpeed() {
        assertTrue(enable(AacAc3Policy.ON, profile = false, codec = "aac", channels = 6, route = true))
        assertFalse(enable(AacAc3Policy.OFF, profile = true, codec = "aac", channels = 6, route = true))
        assertFalse(enable(AacAc3Policy.ON, profile = true, codec = "aac", channels = 6, route = true, forceStereo = true))
        assertFalse(enable(AacAc3Policy.ON, profile = true, codec = "aac", channels = 6, route = true, speedIsOne = false))
        assertFalse(enable(AacAc3Policy.AUTO, profile = true, codec = "aac", channels = 6, route = true, preferFfmpeg = true))
        assertTrue(enable(AacAc3Policy.ON, profile = true, codec = "aac", channels = 6, route = true, preferFfmpeg = true))
    }

    @Test
    fun verifiedHdmiStillAllowsAc3WhenTheQuerySaysStereo() {
        assertTrue(AacAc3Policy.sinkAllows(profile = true, allowBitstream = false, hdmiFamily = true))
        assertFalse(AacAc3Policy.sinkAllows(profile = false, allowBitstream = false, hdmiFamily = true))
        assertFalse(AacAc3Policy.sinkAllows(profile = true, allowBitstream = false, hdmiFamily = false))
        assertTrue(AacAc3Policy.sinkAllows(profile = false, allowBitstream = true, hdmiFamily = false))
    }

    @Test
    fun labels() {
        assertEquals("自动", AacAc3Policy.label(AacAc3Policy.AUTO))
        assertEquals("开", AacAc3Policy.label(AacAc3Policy.ON))
        assertEquals("关", AacAc3Policy.label(AacAc3Policy.OFF))
    }

    private fun enable(
        mode: Int,
        profile: Boolean,
        codec: String,
        channels: Int,
        route: Boolean,
        forceStereo: Boolean = false,
        speedIsOne: Boolean = true,
        preferFfmpeg: Boolean = false,
    ) = AacAc3Policy.enable(mode, profile, codec, channels, speedIsOne, forceStereo, route, preferFfmpeg)
}
