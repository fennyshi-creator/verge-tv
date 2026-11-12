package com.xxxx.emby_tv.ui.focus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Android's default bring-into-view parks the focused child at 30% down the screen.
 * That fights a scroll-to-zero: the first row comes back, then the pivot pulls it
 * down and the top of the page never returns. While [pinToStart] is set, the spec
 * scrolls all the way to the start and ignores that pivot.
 */
@Stable
class TvBringIntoView {
    var pinToStart by mutableStateOf(false)

    fun distance(
        childOffset: Float,
        childSize: Float,
        containerSize: Float,
        viewportScroll: Float,
        scrolledPastStart: Boolean,
    ): Float = bringIntoViewDistance(
        childOffset = childOffset,
        childSize = childSize,
        containerSize = containerSize,
        pinToStart = pinToStart,
        viewportScroll = viewportScroll,
        scrolledPastStart = scrolledPastStart,
    )
}

fun bringIntoViewDistance(
    childOffset: Float,
    childSize: Float,
    containerSize: Float,
    pinToStart: Boolean,
    viewportScroll: Float,
    scrolledPastStart: Boolean,
): Float {
    if (pinToStart) {
        return if (scrolledPastStart) -1_000_000f else -viewportScroll
    }
    val childBottom = childOffset + childSize
    return when {
        childOffset >= 0f && childBottom <= containerSize -> 0f
        childOffset < 0f -> childOffset
        else -> (childBottom - containerSize).coerceAtLeast(0f)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideTvBringIntoView(
    controller: TvBringIntoView,
    viewportScroll: () -> Float,
    scrolledPastStart: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val spec = androidx.compose.runtime.remember(controller) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                return controller.distance(
                    childOffset = offset,
                    childSize = size,
                    containerSize = containerSize,
                    viewportScroll = viewportScroll(),
                    scrolledPastStart = scrolledPastStart(),
                )
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

fun LazyListState.scrolledPastStart(): Boolean = firstVisibleItemIndex > 0

fun LazyListState.viewportScroll(): Float = firstVisibleItemScrollOffset.toFloat()
