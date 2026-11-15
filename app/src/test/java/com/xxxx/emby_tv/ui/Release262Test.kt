package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.connect.PairingPage
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.player.DolbyPassthrough
import com.xxxx.emby_tv.ui.player.PassthroughRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release262Test {
    private fun hdmi(encodings: Set<Int>) = PassthroughRoute.Device(PassthroughRoute.TYPE_HDMI, encodings)
    private fun speaker() = PassthroughRoute.Device(PassthroughRoute.TYPE_BUILTIN_SPEAKER, setOf(DolbyPassthrough.ENCODING_PCM_16BIT))

    @Test
    fun speakersAndHeadphonesDecodeEvenIfADirectQuerySaysYes() {
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_BUILTIN_SPEAKER, emptySet(), directQuery = true))
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_WIRED_HEADPHONES, setOf(DolbyPassthrough.ENCODING_E_AC3), directQuery = true))
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_BLUETOOTH_A2DP, emptySet(), directQuery = false))
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_USB_HEADSET, emptySet(), directQuery = false))
        val active = PassthroughRoute.allowActive(listOf(speaker(), hdmi(setOf(DolbyPassthrough.ENCODING_E_AC3))), PassthroughRoute.MASK_SPEAKER, directQuery = true)
        assertFalse(active)
    }

    @Test
    fun hdmiKeepsBitstreamWhenTheDirectQueryIsAFalseNegative() {
        assertTrue(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI, emptySet(), directQuery = false))
        assertTrue(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI_ARC, setOf(DolbyPassthrough.ENCODING_E_AC3), directQuery = false))
        assertTrue(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI_EARC, setOf(PassthroughRoute.ENCODING_IEC61937), directQuery = false))
        val dune = PassthroughRoute.allowActive(listOf(hdmi(emptySet())), PassthroughRoute.MASK_HDMI, directQuery = false)
        assertTrue(dune)
    }

    @Test
    fun anEndpointThatListsNoDolbyEncodingDecodes() {
        val pcmOnly = setOf(DolbyPassthrough.ENCODING_PCM_16BIT)
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI, pcmOnly, directQuery = false))
        assertTrue(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI, pcmOnly, directQuery = true))
        val switched = PassthroughRoute.allowActive(listOf(speaker()), 0, directQuery = false)
        assertFalse(switched)
        assertTrue(PassthroughRoute.allowActive(emptyList(), 0, directQuery = false))
    }

    @Test
    fun homeSeamClearsTheStillAndThePairingSuccessPageIsDark() {
        assertTrue(HomeMask.seam.value >= 200f)
        assertEquals(0f, HomeMask.stillAlpha(1f))
        assertTrue(HomeMask.stillAlpha(0.5f) > 0.5f)
        assertEquals(800, HomeMask.homeScroll(rowTop = 860, index = 2, stagePx = 60, heroBottom = 800, maxScroll = 4000))
        val page = PairingPage.successDocument("Configuration Sent!", "You can verify the login on your TV.", "配置已发送！", "请在电视上验证登录。")
        assertTrue(page.contains(PairingPage.background))
        assertTrue(page.contains("配置已发送！"))
        assertFalse(PairingPage.usesLegacyPurple(page))
        assertFalse(page.contains("var(--theme-primary)"))
        assertFalse(PairingPage.usesLegacyPurple(PairingPage.css()))
    }
}
