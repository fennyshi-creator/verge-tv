package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Swap tab or season content without moving the focused control.
 * Keep focus on the tab or chip that sits outside this crossfade.
 */
@Composable
fun <T> ContentCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "content",
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = Motion.fade(),
        label = label,
        content = content,
    )
}
