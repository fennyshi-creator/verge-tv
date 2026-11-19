package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.home.HomeStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Release104Test {
    @Test
    fun firstScreenScrimLandsOnThePageColorBeforeTheStillEnds() {
        assertEquals(0.58f, HomeMask.CLEAR_UNTIL)
        assertEquals(0f, HomeMask.firstScreenDarken(HomeMask.CLEAR_UNTIL))
        assertEquals(1f, HomeMask.firstScreenDarken(1f))
        val bluish = androidx.compose.ui.graphics.Color(0xFF1C222C)
        val mid = androidx.compose.ui.graphics.Color(0xFF3A5A8A)
        val tint = HomeMask.scrimColor(bluish)
        val midTint = HomeMask.scrimColor(mid)
        assertNotEquals(HomeStyle.background, tint)
        assertNotEquals(HomeStyle.background, midTint)
        for (swatch in listOf(tint, midTint, HomeMask.scrimColor(null))) {
            val stops = HomeMask.firstScreenStops(swatch)
            val clear = stops.first { it.first == HomeMask.CLEAR_UNTIL }.second
            assertEquals(0f, clear.alpha)
            val fade = stops.first { it.first == 0.72f }.second
            assertTrue(fade.alpha > 0f && fade.alpha < 1f)
            val landed = stops.first { it.first == HomeMask.LAND_AT }.second
            val bottom = stops.last().second
            assertEquals(HomeStyle.background, landed)
            assertEquals(1f, landed.alpha)
            assertEquals(HomeStyle.background, bottom)
            assertEquals(1f, bottom.alpha)
            assertTrue(stops.last().first == 1f)
            assertTrue(HomeMask.LAND_AT < 1f)
        }
        assertEquals(HomeStyle.background, HomeMask.scrimColor(androidx.compose.ui.graphics.Color(0xFFB7D9F2)))
    }
}
