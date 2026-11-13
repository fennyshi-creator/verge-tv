package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Focused artwork rises and grows a little. The same ease as the rest of the
 * shell. Unfocused cards stay at 1x. Avatars use the smaller pair so a round
 * face does not collide with its caption.
 */
object FocusLift {
    const val SCALE = 1.06f
    const val AVATAR_SCALE = 1.03f
    val rise = 10.dp
    val avatarRise = 4.dp
}

@Composable
fun Modifier.tvFocusLift(
    active: Boolean,
    scaleTo: Float = FocusLift.SCALE,
    rise: Dp = FocusLift.rise,
): Modifier {
    val scale by animateFloatAsState(if (active) scaleTo else 1f, Motion.focus(), label = "focus-lift-scale")
    val riseDp by animateDpAsState(if (active) rise else 0.dp, Motion.focus(), label = "focus-lift-rise")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        translationY = -riseDp.toPx()
    }
}
