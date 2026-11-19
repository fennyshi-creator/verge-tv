package com.xxxx.emby_tv.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose focus runtime for the windowed library rows. Robolectric hosts the
 * composition; this is not a television and not an emulator.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryRowFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private class Harness(val total: Int) {
        var cursor by mutableStateOf(LibraryNav.Cursor(0, 0, total))
        val requesters = LibraryRequesters()
        var repeat = false
    }

    private fun setRows(host: Harness) {
        compose.setContent {
            val cursor = host.cursor
            LibraryKeyedWindow(
                rowCount = host.total,
                focusedRow = cursor.focused,
                pendingRow = cursor.pending,
                stridePx = 48,
                requesters = host.requesters,
                edgeUp = null,
                focusedNow = { host.cursor.focused },
                repeatNow = { host.repeat },
                onStep = { delta, repeat ->
                    host.repeat = repeat
                    host.cursor = LibraryNav.step(host.cursor, delta)
                },
                onFocused = { row ->
                    host.cursor = LibraryNav.focused(host.cursor, row)
                },
                onRetreat = { landed ->
                    host.cursor = LibraryNav.Cursor(landed, landed, host.total)
                },
                onArmRepeat = {},
                onHorizontal = { false },
                onOpen = {},
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp))
            }
        }
    }

    @Test
    fun downPastTheWindowBoundaryVisitsEveryRow() {
        val host = Harness(16)
        setRows(host)
        compose.onNodeWithTag("library-row-0").requestFocus()
        compose.waitForIdle()
        val seen = ArrayList<Int>()
        seen.add(host.cursor.focused)
        repeat(15) {
            val tag = "library-row-${host.cursor.focused}"
            compose.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            seen.add(host.cursor.focused)
        }
        assertEquals((0..15).toList(), seen)
        assertEquals(listOf(3, 4, 5), seen.subList(3, 6))
        assertEquals(0, compose.onAllNodesWithTag("library-row-0").fetchSemanticsNodes().size)
    }

    @Test
    fun upReturnsAndReversalDoesNotStick() {
        val host = Harness(16)
        setRows(host)
        compose.onNodeWithTag("library-row-0").requestFocus()
        compose.waitForIdle()
        repeat(15) {
            compose.onNodeWithTag("library-row-${host.cursor.focused}")
                .performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        assertEquals(15, host.cursor.focused)
        repeat(15) {
            compose.onNodeWithTag("library-row-${host.cursor.focused}")
                .performKeyInput { pressKey(Key.DirectionUp) }
            compose.waitForIdle()
        }
        assertEquals(0, host.cursor.focused)
        compose.onNodeWithTag("library-row-0").performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        compose.onNodeWithTag("library-row-${host.cursor.focused}")
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals(0, host.cursor.focused)
    }
}
