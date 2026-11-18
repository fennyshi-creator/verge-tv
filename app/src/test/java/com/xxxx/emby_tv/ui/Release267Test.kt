package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.connect.PairingPage
import com.xxxx.emby_tv.ui.connect.SearchConfirm
import com.xxxx.emby_tv.ui.home.HomeMark
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Release267Test {
    @Test
    fun firstScreenMaskRisesFromTheBottomAndFallsBackToThePageColor() {
        assertFalse(HomeMask.horizontalHeroCutoff())
        assertEquals(0f, HomeMask.firstScreenDarken(0f))
        assertEquals(0f, HomeMask.firstScreenDarken(0.55f))
        assertEquals(0f, HomeMask.firstScreenDarken(HomeMask.CLEAR_UNTIL))
        assertTrue(HomeMask.firstScreenDarken(0.82f) > 0.15f)
        assertTrue(HomeMask.firstScreenDarken(1f) > HomeMask.firstScreenDarken(0.9f))
        assertTrue(HomeMask.firstScreenDarken(0.9f) > HomeMask.firstScreenDarken(0.78f))
        assertTrue(HomeMask.firstScreenDarken(1f) < 1f)
        val fallback = HomeMask.scrimColor(null)
        assertEquals(HomeStyle.background, fallback)
        val swatch = androidx.compose.ui.graphics.Color(0xFF335577)
        assertEquals(swatch, HomeMask.scrimColor(swatch))
    }

    @Test
    fun ratingLineMatchesTheProjectorCopyAndOmitsBlank() {
        assertEquals("分级为 R", EmbyPresentation.ratingLine("R"))
        assertEquals("分级为 PG-13", EmbyPresentation.ratingLine(" PG-13 "))
        assertNull(EmbyPresentation.ratingLine(null))
        assertNull(EmbyPresentation.ratingLine(" "))
        assertEquals(
            "分级为 R",
            EmbyPresentation.ratingLine(EmbyPresentation.playbackRating(null, "R", "PG")),
        )
        assertNull(EmbyPresentation.ratingLine(EmbyPresentation.playbackRating(" ", null, " ")))
        assertEquals(0f, EmbyPresentation.ratingAlpha(-1))
        assertEquals(0f, EmbyPresentation.ratingAlpha(5000))
    }

    @Test
    fun searchPairingAndConfirmStayOffPurple() {
        val page = PairingPage.searchDocument()
        assertFalse(PairingPage.usesLegacyPurple(page))
        assertTrue(page.contains(PairingPage.background))
        assertTrue(page.contains(PairingPage.buttonBackground))
        assertTrue(page.contains(com.xxxx.emby_tv.ui.connect.ConnectForm.PHONE_SEARCH_SUBMIT))
        assertFalse(page.contains("--theme-primary"))
        assertFalse(SearchConfirm.usesThemePrimary())
        assertEquals(0xFFF4F1EA, SearchConfirm.background)
        assertEquals(0xFF141210, SearchConfirm.text)
    }

    @Test
    fun homeMarkIsTheCroppedEmblem() {
        assertEquals("emby_mark", HomeMark.DRAWABLE)
        assertFalse(HomeMark.includesWordmark())
        assertTrue(HomeMark.cropStopsBeforeWordmark())
        assertTrue(HomeMark.CROP_RIGHT < HomeMark.WORDMARK_START)
        assertTrue(HomeMark.ASPECT in 0.9f..1.1f)
    }
}
