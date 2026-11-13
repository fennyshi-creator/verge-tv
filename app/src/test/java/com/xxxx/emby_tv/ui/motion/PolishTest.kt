package com.xxxx.emby_tv.ui.motion

import com.xxxx.emby_tv.ui.home.ArtworkWash
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishTest {
    @Test
    fun heroMetaOmitsMissingPieces() {
        assertEquals(
            "剧情 · 2024 · ★ 8.1",
            EmbyPresentation.heroMetaLine(listOf("剧情"), 2024, 8.14),
        )
        assertEquals("2024", EmbyPresentation.heroMetaLine(emptyList(), 2024, null))
        assertEquals("剧情 · ★ 7.0", EmbyPresentation.heroMetaLine(listOf("剧情", ""), null, 7.0))
        assertEquals("", EmbyPresentation.heroMetaLine(null, null, 0.0))
    }

    @Test
    fun firstCardHasNoLeftPeekAndLaterCardsDo() {
        val inset = 32
        val gap = 14
        val stride = 138
        assertEquals(0, LeadingPeek.peekWidth(0, inset, gap))
        assertEquals(inset, LeadingPeek.slide(0, inset, stride))
        assertEquals(inset - gap, LeadingPeek.peekWidth(2, inset, gap))
        assertTrue(LeadingPeek.peekWidth(2, inset, gap) > 0)
        assertEquals(inset, LeadingPeek.focusedX(inset, slot = 0, stridePx = stride))
        val window = 2
        val focused = LeadingPeek.slide(window, inset, stride) + window * stride
        assertEquals(inset, focused)
    }

    @Test
    fun seasonPillIsGrayUntilTheTabsHaveFocus() {
        assertEquals(FocusPill.Face.Selected, FocusPill.face(rowFocused = false, current = true))
        assertEquals(FocusPill.Face.Focused, FocusPill.face(rowFocused = true, current = true))
        assertEquals(FocusPill.Face.Idle, FocusPill.face(rowFocused = true, current = false))
        assertEquals(FocusPill.Face.Idle, FocusPill.face(rowFocused = false, current = false))
    }

    @Test
    fun menusStayGrayUntilFocusIsOnThem() {
        assertEquals(FocusPill.Face.Idle, FocusPill.menuFace(menuFocused = false, slot = 1, index = 1))
        assertEquals(FocusPill.Face.Focused, FocusPill.menuFace(menuFocused = true, slot = 1, index = 1))
        assertEquals(FocusPill.Face.Idle, FocusPill.menuFace(menuFocused = true, slot = 1, index = 0))
    }

    @Test
    fun washFallsBackWhenNoSwatch() {
        assertNull(ArtworkWash.argb(null, null, null))
        assertEquals(0x112233, ArtworkWash.argb(0x112233, 0x445566, null))
        assertEquals(0x445566, ArtworkWash.argb(null, 0x445566, 0x778899))
        assertNull(ArtworkWash.sampleMuted(IntArray(0)))
        assertNull(ArtworkWash.sampleMuted(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))
        val muted = ArtworkWash.sampleMuted(intArrayOf(0xFF3A2430.toInt(), 0xFF4A3040.toInt(), 0xFF2A2030.toInt()))
        assertTrue(muted != null)
    }
}
