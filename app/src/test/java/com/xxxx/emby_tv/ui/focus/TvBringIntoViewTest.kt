package com.xxxx.emby_tv.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class TvBringIntoViewTest {
    @Test
    fun pinToStartCancelsThePivotAndReturnsToZero() {
        val scrolled = 860f
        val distance = bringIntoViewDistance(
            childOffset = -420f,
            childSize = 180f,
            containerSize = 540f,
            pinToStart = true,
            viewportScroll = scrolled,
            scrolledPastStart = false,
        )
        assertEquals(0f, scrolled + distance, 0.1f)
    }

    @Test
    fun visibleChildDoesNotUseAThirtyPercentPivot() {
        val distance = bringIntoViewDistance(
            childOffset = 40f,
            childSize = 120f,
            containerSize = 540f,
            pinToStart = false,
            viewportScroll = 0f,
            scrolledPastStart = false,
        )
        assertEquals(0f, distance, 0.1f)
    }

    @Test
    fun downThenUpLandsOnTheFirstRowAtOffsetZero() {
        val rows = listOf(72f, 210f, 190f, 280f, 280f)
        var index = 1
        var scroll = 0f
        val viewport = 540f
        repeat(3) {
            index += 1
            scroll = revealRow(rows, index, viewport)
        }
        assertEquals(4, index)
        repeat(3) {
            index -= 1
            scroll = if (index <= 1) 0f else revealRow(rows, index, viewport)
        }
        assertEquals(1, index)
        assertEquals(0f, scroll, 0.1f)
    }

    private fun revealRow(heights: List<Float>, index: Int, viewport: Float): Float {
        val top = heights.take(index).sum()
        val bottom = top + heights[index]
        return when {
            bottom <= viewport -> 0f
            else -> top
        }
    }
}
