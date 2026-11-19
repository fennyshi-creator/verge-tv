package com.xxxx.emby_tv.ui.library

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.xxxx.emby_tv.ui.focus.navigationSound
import com.xxxx.emby_tv.ui.motion.LibraryWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Logical library-row cursor. [pending] walks one row per key.
 * [focused] advances only when that row actually takes focus, so a window
 * shift cannot skip a row or leave the cursor ahead of the screen.
 */
object LibraryNav {
    data class Cursor(val focused: Int, val pending: Int, val total: Int) {
        fun clamped(): Cursor {
            if (total <= 0) return copy(focused = 0, pending = 0)
            return copy(
                focused = focused.coerceIn(0, total - 1),
                pending = pending.coerceIn(0, total - 1),
            )
        }
    }

    fun step(cursor: Cursor, delta: Int): Cursor {
        val current = cursor.clamped()
        if (current.total <= 0 || delta == 0) return current
        val ahead = current.pending - current.focused
        val reverse = ahead != 0 && (ahead > 0) != (delta > 0)
        val base = if (reverse) current.focused else current.pending
        val next = (base + delta).coerceIn(0, current.total - 1)
        return current.copy(pending = next)
    }

    fun focused(cursor: Cursor, row: Int): Cursor {
        val current = cursor.clamped()
        if (row !in 0 until current.total) return current
        return current.copy(focused = row)
    }

    /** Every logical row focus must visit, in order. */
    fun steps(from: Int, to: Int): List<Int> {
        if (from == to) return emptyList()
        val dir = if (to > from) 1 else -1
        val out = ArrayList<Int>()
        var i = from
        while (i != to) {
            i += dir
            out.add(i)
        }
        return out
    }

    fun composeRange(focused: Int, pending: Int, total: Int): IntRange {
        if (total <= 0) return IntRange.EMPTY
        val lo = minOf(focused, pending).coerceIn(0, total - 1)
        val hi = maxOf(focused, pending).coerceIn(0, total - 1)
        val start = (lo - LibraryWindow.BEFORE).coerceAtLeast(0)
        val end = (hi + LibraryWindow.AFTER).coerceAtMost(total - 1)
        return start..end
    }

    /**
     * Move focus one logical row at a time toward [target].
     * Returns the row that actually holds focus. A failed request does not advance.
     */
    suspend fun follow(
        focused: () -> Int,
        target: Int,
        total: Int,
        request: suspend (Int) -> Boolean,
        arrived: suspend (Int) -> Boolean,
    ): Int {
        if (total <= 0) return 0
        val goal = target.coerceIn(0, total - 1)
        var guard = 0
        while (focused() != goal && guard++ < total + 2) {
            val current = focused().coerceIn(0, total - 1)
            if (current == goal) break
            val step = (current + if (goal > current) 1 else -1).coerceIn(0, total - 1)
            if (step == current) break
            if (!request(step) || !arrived(step)) return focused().coerceIn(0, total - 1)
        }
        return focused().coerceIn(0, total - 1)
    }
}

class LibraryRequesters {
    private val pool = ArrayList<FocusRequester>()

    fun get(index: Int): FocusRequester {
        while (pool.size <= index) pool.add(FocusRequester())
        return pool[index]
    }
}

/**
 * Windowed poster rows. Each logical index is a composition [key], so shifting
 * the window moves nodes instead of reusing a focused slot for another row.
 */
@Composable
internal fun LibraryKeyedWindow(
    rowCount: Int,
    focusedRow: Int,
    pendingRow: Int,
    stridePx: Int,
    requesters: LibraryRequesters,
    edgeUp: FocusRequester?,
    focusedNow: () -> Int,
    repeatNow: () -> Boolean,
    onStep: (delta: Int, repeat: Boolean) -> Unit,
    onFocused: (Int) -> Unit,
    onRetreat: (Int) -> Unit,
    onArmRepeat: (Boolean) -> Unit,
    onHorizontal: (Int) -> Boolean,
    onOpen: () -> Unit,
    modifierFor: (Int) -> Modifier = { Modifier },
    content: @Composable (Int) -> Unit,
) {
    val density = LocalDensity.current
    val range = LibraryNav.composeRange(focusedRow, pendingRow, rowCount)
    LaunchedEffect(pendingRow, rowCount) {
        if (rowCount <= 0) return@LaunchedEffect
        val target = pendingRow.coerceIn(0, rowCount - 1)
        val landed = LibraryNav.follow(
            focused = focusedNow,
            target = target,
            total = rowCount,
            request = { step ->
                withFrameNanos { }
                onArmRepeat(repeatNow())
                val once = runCatching { requesters.get(step).requestFocus() }.isSuccess
                if (once) {
                    true
                } else {
                    withFrameNanos { }
                    runCatching { requesters.get(step).requestFocus() }.isSuccess
                }
            },
            arrived = { step ->
                withTimeoutOrNull(400) {
                    snapshotFlow { focusedNow() }.first { it == step }
                } != null
            },
        )
        if (landed != target) onRetreat(landed)
    }
    if (range.first > 0 && stridePx > 0) {
        key("pad-above") {
            Spacer(Modifier.height(with(density) { (range.first * stridePx).toDp() }))
        }
    }
    for (index in range) {
        key(index) {
            androidx.compose.foundation.layout.Box(
                modifierFor(index)
                    .fillMaxWidth()
                    .testTag("library-row-$index")
                    .focusRequester(requesters.get(index))
                    .focusProperties {
                        up = if (index == 0) edgeUp ?: FocusRequester.Cancel else requesters.get(index - 1)
                        down = if (index >= rowCount - 1) FocusRequester.Cancel else requesters.get(index + 1)
                    }
                    .onFocusChanged { if (it.isFocused) onFocused(index) }
                    .focusable()
                    .navigationSound()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val repeat = event.nativeKeyEvent.repeatCount > 0
                        when (event.key) {
                            Key.DirectionDown -> {
                                onArmRepeat(repeat)
                                onStep(1, repeat)
                                true
                            }
                            Key.DirectionUp -> {
                                if (index == 0 && edgeUp != null) {
                                    try {
                                        edgeUp.requestFocus()
                                    } catch (_: IllegalStateException) {
                                    }
                                } else {
                                    onArmRepeat(repeat)
                                    onStep(-1, repeat)
                                }
                                true
                            }
                            Key.DirectionLeft -> onHorizontal(-1)
                            Key.DirectionRight -> onHorizontal(1)
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                onOpen()
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                content(index)
            }
        }
    }
    val tail = if (range.isEmpty()) 0 else rowCount - range.last - 1
    if (tail > 0 && stridePx > 0) {
        key("pad-below") {
            Spacer(Modifier.height(with(density) { (tail * stridePx).toDp() }))
        }
    }
}

@Composable
internal fun rememberLibraryRequesters(): LibraryRequesters = remember { LibraryRequesters() }
