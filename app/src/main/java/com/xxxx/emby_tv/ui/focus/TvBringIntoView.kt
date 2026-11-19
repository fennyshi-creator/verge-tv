package com.xxxx.emby_tv.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xxxx.emby_tv.ui.motion.Motion

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
            override val scrollAnimationSpec: AnimationSpec<Float> = Motion.pageFloat

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

/**
 * Glide a row to the top of the list. One in-flight animation is the caller's job
 * (cancel the previous coroutine). The last call still snaps to offset 0 when asked,
 * so a return-to-top cannot stop short of the start.
 */
suspend fun LazyListState.glideToItem(index: Int, pinToZero: Boolean = false) {
    val spec = Motion.pageFloat
    repeat(5) {
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item != null) {
            if (item.offset != 0) animateScrollBy(item.offset.toFloat(), spec)
            if (pinToZero) scrollToItem(0, 0)
            return
        }
        val forward = index > firstVisibleItemIndex
        val page = layoutInfo.viewportSize.height.coerceAtLeast(1) * 0.82f
        animateScrollBy(if (forward) page else -page, spec)
    }
    scrollToItem(index, 0)
    if (pinToZero) scrollToItem(0, 0)
}
