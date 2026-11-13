package com.xxxx.emby_tv.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalHomeFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** When true, generated artwork is painted as a soft wash so a blurred backdrop reads in previews. */
val LocalArtworkSoften = staticCompositionLocalOf { false }

object PosterRowFit {
    /** How many poster widths fit, counting a partial card on the right. */
    fun visibleCount(viewport: Int, inset: Int, card: Int, gap: Int): Float {
        if (card + gap <= 0) return 0f
        return (viewport - inset + gap).toFloat() / (card + gap)
    }

    /**
     * Homepage poster rows: a left peek, about four posters, and a right half-peek.
     * Width is derived from the real viewport so the count survives the focus lift.
     */
    /**
     * Unfocused slots. A left peek, about four posters, and a right half-peek.
     * 4.85 rendered as two-and-a-half huge cards on the projector, so the slot
     * is smaller and then capped so it cannot exceed a fifth of the screen.
     */
    /** About six and a quarter posters across, counting the side peeks. */
    const val TARGET_VISIBLE = 6.35f
    const val MAX_SLOT_FRACTION = 0.17f

    fun cardWidthPx(viewportPx: Int, insetPx: Int, gapPx: Int, visible: Float = TARGET_VISIBLE): Int {
        if (visible <= 0.1f) return 1
        val raw = (((viewportPx - insetPx + gapPx) / visible) - gapPx).toInt()
        val cap = (viewportPx * MAX_SLOT_FRACTION).toInt()
        return raw.coerceAtMost(cap).coerceAtLeast(1)
    }
}

object HomeStyle {
    val background = Color(0xFF141210)
    val title = Color(0xFFF2F2F2)
    val meta = Color(0xFFA6A6A6)
    val chip = Color(0xE6121212)
    val progressTrack = Color(0x59FFFFFF)

    val gutter = 32.dp
    val rowGap = 20.dp
    val continueWidth = 186.dp
    val continueHeight = 104.dp
    val libraryWidth = 272.dp
    val libraryHeight = 118.dp
    val posterWidth = 124.dp
    val posterHeight = 186.dp
    val cardCorner = 14.dp
    val libraryCorner = 13.dp

    val sectionSize = 22.sp
    val cardTitle = 14.sp
    val metaSize = 12.sp
    val chipSize = 10.sp
    val navSize = 16.sp

    val navPill = Color(0xFFE8E8EA)
    val navPillText = Color(0xFF161616)
    val logoGreen = Color(0xFF52B54B)
    val focusStroke = 2.5.dp
}
