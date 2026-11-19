package com.xxxx.emby_tv.ui.library

import com.xxxx.emby_tv.ui.motion.LibraryWindow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * State machine for the library row cursor. This does not drive a Compose focus
 * tree; [LibraryRowFocusTest] does that when the host can run a composition.
 */
class LibraryNavTest {
    private fun press(cursor: LibraryNav.Cursor, delta: Int, times: Int = 1): LibraryNav.Cursor {
        var current = cursor
        repeat(times) { current = LibraryNav.step(current, delta) }
        return current
    }

    @Test
    fun singleDownVisitsEveryRowPastTheOldSkip() = runBlocking {
        val total = 16
        var cursor = LibraryNav.Cursor(focused = 0, pending = 0, total = total)
        val seen = ArrayList<Int>()
        repeat(15) {
            cursor = LibraryNav.step(cursor, +1)
            val landed = LibraryNav.follow(
                focused = { cursor.focused },
                target = cursor.pending,
                total = total,
                request = { step ->
                    seen.add(step)
                    cursor = LibraryNav.focused(cursor, step)
                    true
                },
                arrived = { true },
            )
            cursor = cursor.copy(focused = landed)
        }
        assertEquals((1..15).toList(), seen)
        assertEquals(15, cursor.focused)
        assertTrue(seen.contains(3))
        assertTrue(seen.contains(4))
        assertEquals(listOf(3, 4, 5), seen.subList(2, 5))
    }

    @Test
    fun upReturnsThroughEveryRowAndStopsAtTheTop() = runBlocking {
        val total = 16
        var cursor = LibraryNav.Cursor(15, 15, total)
        val seen = ArrayList<Int>()
        repeat(20) {
            val before = cursor.pending
            cursor = LibraryNav.step(cursor, -1)
            if (cursor.pending == before) return@repeat
            val landed = LibraryNav.follow(
                focused = { cursor.focused },
                target = cursor.pending,
                total = total,
                request = { step ->
                    seen.add(step)
                    cursor = LibraryNav.focused(cursor, step)
                    true
                },
                arrived = { true },
            )
            cursor = cursor.copy(focused = landed)
        }
        assertEquals((14 downTo 0).toList(), seen)
        assertEquals(0, cursor.focused)
        assertEquals(0, LibraryNav.step(cursor, -1).pending)
    }

    @Test
    fun rapidReversalDropsTheQueuedDirection() {
        var cursor = LibraryNav.Cursor(focused = 4, pending = 8, total = 16)
        cursor = LibraryNav.step(cursor, -1)
        assertEquals(3, cursor.pending)
        assertEquals(4, cursor.focused)
        assertEquals(listOf(3), LibraryNav.steps(cursor.focused, cursor.pending))
    }

    @Test
    fun holdAdvancesOneLogicalRowPerEvent() {
        var cursor = LibraryNav.Cursor(0, 0, 16)
        val pending = ArrayList<Int>()
        repeat(8) {
            cursor = LibraryNav.step(cursor, +1)
            pending.add(cursor.pending)
        }
        assertEquals((1..8).toList(), pending)
        assertEquals(LibraryNav.steps(0, 8), (1..8).toList())
    }

    @Test
    fun windowKeepsTheFocusedRowAndTheNextOne() {
        val moving = LibraryNav.composeRange(focused = 3, pending = 4, total = 16)
        assertTrue(3 in moving && 4 in moving)
        assertFalse(0 in moving)
        val deep = LibraryNav.composeRange(focused = 12, pending = 12, total = 16)
        assertEquals(10..15, deep)
        assertEquals(13..15, LibraryNav.composeRange(15, 15, 16))
        assertTrue(LibraryWindow.range(3, 16).contains(3))
    }

    @Test
    fun appendAndPartialLastRowStayAddressable() {
        var cursor = LibraryNav.Cursor(focused = 9, pending = 9, total = 10)
        cursor = cursor.copy(total = 15).clamped()
        cursor = LibraryNav.step(cursor, +1)
        assertEquals(10, cursor.pending)
        val last = LibraryNav.Cursor(15, 15, 16)
        assertEquals(15, LibraryNav.step(last, +1).pending)
        assertEquals(listOf(12), LibraryNav.steps(11, 12))
    }

    @Test
    fun failedRequestDoesNotAdvanceFocus() = runBlocking {
        val cursor = LibraryNav.Cursor(4, 5, 16)
        val landed = LibraryNav.follow(
            focused = { cursor.focused },
            target = cursor.pending,
            total = 16,
            request = { false },
            arrived = { true },
        )
        assertEquals(4, landed)
    }
}
