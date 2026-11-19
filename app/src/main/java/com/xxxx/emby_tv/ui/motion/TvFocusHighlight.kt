package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * White focus stroke. No zoom. The pulse is read while drawing, so the row
 * around the card does not recompose on every frame.
 */
@Composable
fun TvFocusHighlight(
    modifier: Modifier = Modifier,
    shape: Shape,
    solid: Boolean = false,
    stroke: Dp = 2.5.dp,
) {
    if (solid) {
        Box(modifier.border(stroke, Color.White, shape))
        return
    }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            alpha.animateTo(0.58f, tween(durationMillis = 1100, easing = LinearEasing))
            alpha.animateTo(1f, tween(durationMillis = 1100, easing = LinearEasing))
        }
    }
    Box(
        modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                color = Color.White.copy(alpha = alpha.value),
                style = Stroke(width = stroke.toPx()),
            )
        },
    )
}
