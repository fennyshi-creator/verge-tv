package com.xxxx.emby_tv.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalHomeFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/** When true, generated artwork is painted as a soft wash so a blurred backdrop reads in previews. */
val LocalArtworkSoften = staticCompositionLocalOf { false }

object HomeStyle {
    val background = Color(0xFF141210)
    val title = Color(0xFFF2F2F2)
    val meta = Color(0xFFA6A6A6)
    val chip = Color(0xE6121212)
    val progressTrack = Color(0x59FFFFFF)

    val gutter = 32.dp
    val rowGap = 14.dp
    val continueWidth = 186.dp
    val continueHeight = 104.dp
    val libraryWidth = 272.dp
    val libraryHeight = 118.dp
    val posterWidth = 124.dp
    val posterHeight = 186.dp
    val cardCorner = 11.dp
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
