package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xxxx.emby_tv.ui.home.HomeStyle

/**
 * Focused artwork rises and grows a little. The transform is draw-only:
 * [LAYOUT_SCALE] stays 1 so the row slot never changes width or height.
 * The white stroke lives inside the same layer as the bitmap, so a parent
 * clip cannot peel the picture off the frame and leave a black slit.
 * Circular avatars stay at 1x; they only take the stroke.
 */
object FocusLift {
    const val LAYOUT_SCALE = 1f
    const val SCALE = 1.04f
    const val AVATAR_SCALE = 1f
    val rise = 6.dp

    fun visualScale(avatars: Boolean): Float = if (avatars) AVATAR_SCALE else SCALE

    fun focusedSlotPx(slotPx: Int): Int = slotPx

    /**
     * Space that must sit inside the same clip as the card. Scale grows from
     * the center and the card also rises, so the top overflow is
     * rise + height * (scale - 1) / 2, plus the stroke.
     */
    fun topOverflowPx(cardPx: Int, risePx: Int, strokePx: Int, scale: Float = SCALE): Int {
        val grow = (cardPx * (scale - 1f) / 2f).toInt()
        return (risePx + grow + strokePx).coerceAtLeast(0)
    }

    fun liftFits(headroomPx: Int, cardPx: Int, risePx: Int, strokePx: Int, scale: Float = SCALE): Boolean {
        return headroomPx >= topOverflowPx(cardPx, risePx, strokePx, scale)
    }
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
        clip = false
    }
}

/**
 * Layout size is [modifier]. The bitmap and the focus stroke scale together.
 */
@Composable
fun FocusCard(
    modifier: Modifier,
    focused: Boolean,
    shape: Shape,
    lift: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .tvFocusLift(
                    active = focused && lift,
                    scaleTo = FocusLift.visualScale(avatars = !lift),
                    rise = if (lift) FocusLift.rise else 0.dp,
                ),
        ) {
            Box(Modifier.fillMaxSize().clip(shape), content = content)
            if (focused) {
                TvFocusHighlight(
                    Modifier.fillMaxSize(),
                    shape,
                    stroke = HomeStyle.focusStroke,
                )
            }
        }
    }
}
