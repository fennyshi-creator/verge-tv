package com.xxxx.emby_tv.ui.home

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

/**
 * White focus stroke. The pulse is read in the draw phase so a breathing
 * card does not recompose the row around it.
 */
@Composable
fun BreathingStroke(
    modifier: Modifier = Modifier,
    shape: Shape,
    solid: Boolean = false,
) {
    if (solid) {
        Box(modifier.border(HomeStyle.focusStroke, Color.White, shape))
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
                style = Stroke(width = HomeStyle.focusStroke.toPx()),
            )
        },
    )
}
