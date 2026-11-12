package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** First-frame fade for dialogs, empty states, and loading. No scale. */
@Composable
fun MotionFade(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        modifier = modifier,
        enter = fadeIn(Motion.fade()),
    ) {
        content()
    }
}
