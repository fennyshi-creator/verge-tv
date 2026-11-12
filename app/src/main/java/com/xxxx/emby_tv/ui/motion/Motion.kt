package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

/**
 * Shared motion for the TV shell. One ease, a few durations.
 * Cubic (0.2, 0, 0, 1) settles quickly and then glides, closer to tvOS than a bounce.
 */
object Motion {
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val focusMs = 200
    const val rowMs = 280
    const val pageMs = 300
    const val fadeMs = 240
    const val imageMs = 180

    fun <T> row(): TweenSpec<T> = tween(durationMillis = rowMs, easing = ease)

    fun <T> page(): TweenSpec<T> = tween(durationMillis = pageMs, easing = ease)

    fun <T> fade(): TweenSpec<T> = tween(durationMillis = fadeMs, easing = ease)

    fun <T> focus(): TweenSpec<T> = tween(durationMillis = focusMs, easing = ease)

    /** Short linear step used while a key is repeating, so steps don't restart an ease-out. */
    fun <T> repeatStep(): TweenSpec<T> = tween(durationMillis = 90, easing = androidx.compose.animation.core.LinearEasing)

    val pageFloat: FiniteAnimationSpec<Float> = tween(durationMillis = pageMs, easing = ease)
}
