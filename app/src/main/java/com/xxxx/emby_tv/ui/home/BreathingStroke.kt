package com.xxxx.emby_tv.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.xxxx.emby_tv.ui.motion.TvFocusHighlight

/** Older name for [TvFocusHighlight]. New pages should call that directly. */
@Composable
fun BreathingStroke(
    modifier: Modifier = Modifier,
    shape: Shape,
    solid: Boolean = false,
) {
    TvFocusHighlight(modifier, shape, solid, HomeStyle.focusStroke)
}
