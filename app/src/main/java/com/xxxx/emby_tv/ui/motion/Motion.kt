package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * Shared motion for the TV shell. One ease, a few durations.
 * The curve starts with a small slope so the first frames are readable,
 * then settles. Repeat steps stay short and linear.
 */
object Motion {
    val ease = CubicBezierEasing(0.22f, 0.12f, 0.16f, 1f)

    const val focusMs = 240
    const val rowMs = 340
    const val pageMs = 380
    const val fadeMs = 280
    const val imageMs = 210

    fun <T> row(): TweenSpec<T> = tween(durationMillis = rowMs, easing = ease)

    fun <T> page(): TweenSpec<T> = tween(durationMillis = pageMs, easing = ease)

    fun <T> fade(): TweenSpec<T> = tween(durationMillis = fadeMs, easing = ease)

    fun <T> focus(): TweenSpec<T> = tween(durationMillis = focusMs, easing = ease)

    /** Short linear step used while a key is repeating, so steps don't restart an ease-out. */
    fun <T> repeatStep(): TweenSpec<T> = tween(durationMillis = 90, easing = androidx.compose.animation.core.LinearEasing)

    val pageFloat: FiniteAnimationSpec<Float> = tween(durationMillis = pageMs, easing = ease)
}
