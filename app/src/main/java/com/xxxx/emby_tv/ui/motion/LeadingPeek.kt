package com.xxxx.emby_tv.ui.motion

/**
 * Horizontal leading-slot lock with a left-edge peek.
 * The focused card stays at [insetPx]. When the window has moved, the
 * previous card is clipped by x = 0 so a sliver remains. The first card
 * has no previous item, so the peek width is 0 and the inset stays put.
 */
object LeadingPeek {
    fun slide(windowStart: Int, insetPx: Int, stridePx: Int): Int {
        return insetPx - windowStart.coerceAtLeast(0) * stridePx
    }

    fun focusedX(insetPx: Int, slot: Int, stridePx: Int): Int {
        return insetPx + slot.coerceAtLeast(0) * stridePx
    }

    fun peekWidth(windowStart: Int, insetPx: Int, gapPx: Int): Int {
        if (windowStart <= 0) return 0
        return (insetPx - gapPx).coerceAtLeast(0)
    }
}
