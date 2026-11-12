package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Screen and overlay transitions. No scale. Distances stay small so a page
 * change reads as a crossfade, not a swipe.
 */
object ScreenTransition {
    fun enter(): EnterTransition = fadeIn(Motion.fade()) + slideInHorizontally(Motion.fade()) { it / 24 }

    fun exit(): ExitTransition = fadeOut(Motion.fade())

    fun popEnter(): EnterTransition = fadeIn(Motion.fade())

    fun popExit(): ExitTransition = fadeOut(Motion.fade()) + slideOutHorizontally(Motion.fade()) { it / 24 }

    fun barEnter(): EnterTransition = fadeIn(Motion.fade()) + slideInVertically(Motion.fade()) { it / 3 }

    fun barExit(): ExitTransition = fadeOut(Motion.fade()) + slideOutVertically(Motion.fade()) { it / 3 }

    fun stripEnter(): EnterTransition = fadeIn(Motion.fade()) + slideInVertically(Motion.fade()) { it }

    fun stripExit(): ExitTransition = fadeOut(Motion.fade()) + slideOutVertically(Motion.fade()) { it }

    fun sheetEnter(): EnterTransition = fadeIn(Motion.fade()) + slideInHorizontally(Motion.fade()) { it / 4 }

    fun sheetExit(): ExitTransition = fadeOut(Motion.fade()) + slideOutHorizontally(Motion.fade()) { it / 4 }

    fun cardEnter(): EnterTransition = fadeIn(Motion.fade()) + slideInHorizontally(Motion.fade()) { it / 2 }

    fun cardExit(): ExitTransition = fadeOut(Motion.fade())
}
