package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputPlanTest {
    @Test
    fun compatDecodeDownmixesAndBlocksCompressedOutput() {
        val plan = AudioOutputPlan.choose(
            compatDecode = true,
            passthroughAuto = true,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = true,
            aacMode = AacAc3Policy.ON,
            aacProfile = true,
            codec = "aac",
            channels = 6,
            routeAllowsAc3 = true,
        )
        assertFalse(plan.passthrough)
        assertFalse(plan.aacAc3)
        assertTrue(plan.downmixStereo)
    }

    @Test
    fun passthroughAutoKeepsMultichannelPcmWhenTheRouteCannotCarryBitstream() {
        val plan = AudioOutputPlan.choose(
            compatDecode = false,
            passthroughAuto = true,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = false,
            aacMode = AacAc3Policy.OFF,
            aacProfile = false,
            codec = "eac3",
            channels = 6,
            routeAllowsAc3 = false,
        )
        assertFalse(plan.passthrough)
        assertFalse(plan.aacAc3)
        assertFalse(plan.downmixStereo)
    }

    @Test
    fun passthroughAutoSendsSupportedBitstream() {
        val plan = AudioOutputPlan.choose(
            compatDecode = false,
            passthroughAuto = true,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = true,
            aacMode = AacAc3Policy.OFF,
            aacProfile = false,
            codec = "eac3",
            channels = 6,
            routeAllowsAc3 = true,
        )
        assertTrue(plan.passthrough)
        assertFalse(plan.downmixStereo)
    }

    @Test
    fun passthroughOffDecodesWithoutForcingStereo() {
        val plan = AudioOutputPlan.choose(
            compatDecode = false,
            passthroughAuto = false,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = true,
            aacMode = AacAc3Policy.ON,
            aacProfile = true,
            codec = "aac",
            channels = 6,
            routeAllowsAc3 = true,
        )
        assertFalse(plan.passthrough)
        assertFalse(plan.aacAc3)
        assertFalse(plan.downmixStereo)
    }

    @Test
    fun trueHdOnTvSpeakersUsesTheStereoDownmix() {
        val plan = AudioOutputPlan.choose(
            compatDecode = false,
            passthroughAuto = true,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = false,
            aacMode = AacAc3Policy.OFF,
            aacProfile = false,
            codec = "truehd",
            channels = 8,
            routeAllowsAc3 = false,
            builtInSpeakers = true,
        )
        assertFalse(plan.passthrough)
        assertFalse(plan.aacAc3)
        assertTrue(plan.downmixStereo)
    }

    @Test
    fun trueHdOnHdmiKeepsMultichannelPcm() {
        val plan = AudioOutputPlan.choose(
            compatDecode = false,
            passthroughAuto = true,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = true,
            aacMode = AacAc3Policy.OFF,
            aacProfile = false,
            codec = "truehd",
            channels = 8,
            routeAllowsAc3 = true,
            builtInSpeakers = false,
        )
        assertFalse(plan.downmixStereo)
    }

    @Test
    fun eac3OnTvSpeakersIsNotForcedToStereo() {
        val plan = AudioOutputPlan.choose(
            compatDecode = false,
            passthroughAuto = true,
            speedIsOne = true,
            recoveryStage = 0,
            routeAllowsBitstream = true,
            aacMode = AacAc3Policy.OFF,
            aacProfile = false,
            codec = "eac3",
            channels = 6,
            routeAllowsAc3 = true,
            builtInSpeakers = true,
        )
        assertTrue(plan.passthrough)
        assertFalse(plan.downmixStereo)
    }

    @Test
    fun statusNamesTheDownmixSeparatelyFromTheSource() {
        assertEquals(
            "「AAC 2.0 · 兼容解码」",
            DolbyPassthrough.statusLine(
                com.xxxx.emby_tv.data.model.MediaStreamDto(codec = "aac", channels = 6),
                openedEncoding = 0,
                fellBackToPcm = false,
                downmixStereo = true,
            ),
        )
    }
}
