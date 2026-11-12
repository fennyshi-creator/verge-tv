package com.xxxx.emby_tv.ui.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalHomeFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

object HomeStyle {
    val background = Color(0xFF141210)
    val title = Color(0xFFF2F2F2)
    val meta = Color(0xFFA6A6A6)
    val chip = Color(0xE6121212)
    val progressTrack = Color(0x59FFFFFF)

    val gutter = 32.dp
    val rowGap = 14.dp
    val heroHeight = 138.dp
    val heroCorner = 18.dp
    val continueWidth = 186.dp
    val continueHeight = 104.dp
    val posterWidth = 150.dp
    val posterHeight = 214.dp
    val libraryWidth = 258.dp
    val libraryHeight = 90.dp
    val cardCorner = 11.dp
    val libraryCorner = 13.dp

    val sectionSize = 22.sp
    val cardTitle = 14.sp
    val metaSize = 12.sp
    val chipSize = 10.sp

    const val focusScale = 1.045f
    val focusStroke = 2.5.dp
}
