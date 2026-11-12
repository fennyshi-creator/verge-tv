package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.home.ArtworkPrefetch
import com.xxxx.emby_tv.ui.home.HomeStyle
import kotlin.math.min

/**
 * Horizontal row that keeps the focused card in the leading slot.
 * One press eases. A held key uses a short linear step so the row glides
 * instead of restarting the ease on every repeat. No zoom.
 */
@Composable
fun TvLeadingRow(
    items: List<BaseItemDto>,
    cardWidth: Dp,
    cardHeight: Dp,
    corner: Dp,
    focusRequester: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    onActivate: (BaseItemDto) -> Unit,
    onMenu: () -> Unit,
    onFocused: (() -> Unit)? = null,
    onExitDown: (() -> Unit)? = null,
    onExitUp: (() -> Unit)? = null,
    initialWindowStart: Int = 0,
    initialIndex: Int = -1,
    forceFocused: Boolean = false,
    imageUrls: List<String> = emptyList(),
    backdropUrls: List<String> = emptyList(),
    prefetchRole: ArtRole = ArtRole.Primary,
    caption: @Composable (BaseItemDto) -> Unit,
    art: @Composable BoxScope.(BaseItemDto) -> Unit,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stride = cardWidth + HomeStyle.rowGap
        val usable = maxWidth - HomeStyle.gutter
        val fullSlots = ((usable + HomeStyle.rowGap) / stride).toInt().coerceAtLeast(1)
        val maxStart = (items.size - fullSlots).coerceAtLeast(0)
        val startWindow = if (initialIndex >= 0) {
            initialIndex.coerceIn(0, maxStart)
        } else {
            initialWindowStart.coerceIn(0, maxStart)
        }
        val startSlot = if (initialIndex >= 0) (initialIndex - startWindow).coerceAtLeast(0) else 0
        var windowStart by remember(items.size, fullSlots) { mutableIntStateOf(startWindow) }
        var slot by remember(items.size) { mutableIntStateOf(startSlot) }
        var rowFocused by remember { mutableStateOf(forceFocused) }
        var repeating by remember { mutableStateOf(false) }
        var lastMove by remember { mutableLongStateOf(0L) }
        if (windowStart > maxStart) windowStart = maxStart
        val lastSlot = (min(fullSlots, items.size - windowStart) - 1).coerceAtLeast(0)
        if (slot > lastSlot) slot = lastSlot

        fun noteRepeat() {
            val now = System.currentTimeMillis()
            repeating = now - lastMove < 160L
            lastMove = now
        }

        fun moveRight() {
            val moreRight = items.size - windowStart - fullSlots
            val endSlot = (min(fullSlots, items.size - windowStart) - 1).coerceAtLeast(0)
            if (slot == 0 && moreRight > 0) windowStart++ else if (slot < endSlot) slot++
        }

        fun moveLeft() {
            if (slot > 0) slot-- else if (windowStart > 0) windowStart--
        }

        val slideSpec: androidx.compose.animation.core.FiniteAnimationSpec<Dp> =
            if (repeating) Motion.repeatStep() else Motion.row()
        val slide by animateDpAsState(-(stride * windowStart), slideSpec, label = "leading-slot-slide")
        val ringX by animateDpAsState(stride * slot, slideSpec, label = "leading-slot-ring")
        val shape = RoundedCornerShape(corner)
        val focusedIndex = (windowStart + slot).coerceIn(0, items.lastIndex)
        val prefetchContext = LocalContext.current
        LaunchedEffect(windowStart, focusedIndex, imageUrls, backdropUrls) {
            if (imageUrls.isNotEmpty()) {
                val end = (windowStart + fullSlots + 4).coerceAtMost(imageUrls.size)
                val start = windowStart.coerceIn(0, imageUrls.size)
                ArtworkPrefetch.enqueue(prefetchContext, imageUrls.subList(start, end), prefetchRole)
            }
            if (backdropUrls.isNotEmpty()) {
                ArtworkPrefetch.enqueueBackdrops(
                    prefetchContext,
                    listOfNotNull(backdropUrls.getOrNull(focusedIndex), backdropUrls.getOrNull(focusedIndex + 1)),
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties {
                    up?.let { this.up = it }
                    down?.let { this.down = it }
                }
                .onFocusChanged {
                    rowFocused = it.isFocused || forceFocused
                    if (it.isFocused) onFocused?.invoke()
                }
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> if (onExitDown != null) { onExitDown(); true } else false
                        Key.DirectionUp -> if (onExitUp != null) { onExitUp(); true } else false
                        Key.DirectionRight -> { noteRepeat(); moveRight(); true }
                        Key.DirectionLeft -> if (slot == 0 && windowStart == 0) false else { noteRepeat(); moveLeft(); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onActivate(items[focusedIndex]); true }
                        Key.Menu, Key.Bookmark -> { onMenu(); true }
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = HomeStyle.gutter)
                    .clipToBounds(),
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .offset(x = slide),
                    horizontalArrangement = Arrangement.spacedBy(HomeStyle.rowGap),
                ) {
                    items.forEach { item ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .width(cardWidth)
                                    .height(cardHeight)
                                    .clip(shape)
                                    .focusProperties { canFocus = false }
                                    .clickable { onActivate(item) },
                            ) { art(item) }
                            caption(item)
                        }
                    }
                }
            }
            if (rowFocused || forceFocused) {
                TvFocusHighlight(
                    modifier = Modifier
                        .padding(start = HomeStyle.gutter)
                        .offset(x = ringX)
                        .width(cardWidth)
                        .height(cardHeight),
                    shape = shape,
                    solid = forceFocused && !rowFocused,
                    stroke = HomeStyle.focusStroke,
                )
            }
        }
    }
}
