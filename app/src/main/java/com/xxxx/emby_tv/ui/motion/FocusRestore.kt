package com.xxxx.emby_tv.ui.motion

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.focus.TvBringIntoView
import com.xxxx.emby_tv.ui.focus.glideToItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Remember which row and item opened the next screen. Saved with the
 * back stack, so Back can place that item before the first frame.
 */
@Stable
class FocusRestore internal constructor(
    private val rowState: MutableState<String?>,
    private val itemState: MutableState<String?>,
) {
    var rowKey: String?
        get() = rowState.value
        set(value) { rowState.value = value }

    var itemId: String?
        get() = itemState.value
        set(value) { itemState.value = value }

    fun mark(row: String, id: String?) {
        rowKey = row
        itemId = id
    }

    fun indexIn(items: List<BaseItemDto>): Int = items.indexOfFirst { it.id != null && it.id == itemId }
}

@Composable
fun rememberFocusRestore(): FocusRestore {
    val row = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val item = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    return remember(row, item) { FocusRestore(row, item) }
}

/** Put a restored item on screen, then focus it. No glide, so Back does not travel. */
@Composable
fun RestoreFocus(
    active: Boolean,
    index: Int,
    requester: FocusRequester,
    scrollTo: suspend (Int) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(active, index) {
        if (!active || index < 0) return@LaunchedEffect
        scrollTo(index)
        withFrameNanos { }
        try {
            requester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}

/**
 * Vertical row changes. A held key cancels the previous glide instead of
 * queueing another one. [pin] scrolls to offset 0.
 */
@Stable
class RowGlide(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val bringIntoView: TvBringIntoView,
) {
    private var job: kotlinx.coroutines.Job? = null

    fun to(requester: FocusRequester, index: Int, pin: Boolean) {
        bringIntoView.pinToStart = pin
        job?.cancel()
        job = scope.launch {
            if (pin) listState.glideToItem(0, pinToZero = true) else listState.glideToItem(index)
            if (!isActive) return@launch
            withFrameNanos { }
            try {
                requester.requestFocus()
            } catch (_: IllegalStateException) {
                withFrameNanos { }
                try {
                    requester.requestFocus()
                } catch (_: IllegalStateException) {
                }
            }
            if (pin) listState.scrollToItem(0, 0)
        }
    }
}

@Composable
fun rememberRowGlide(listState: LazyListState, bringIntoView: TvBringIntoView): RowGlide {
    val scope = rememberCoroutineScope()
    return remember(listState, bringIntoView) { RowGlide(scope, listState, bringIntoView) }
}
