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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.focus.navigationSound
import com.xxxx.emby_tv.ui.focus.playNavigationSound
import com.xxxx.emby_tv.ui.home.ArtRole
import com.xxxx.emby_tv.ui.library.LibraryActivation
import com.xxxx.emby_tv.ui.home.ArtworkPrefetch
import com.xxxx.emby_tv.ui.home.HomeStyle
import kotlin.math.min

/**
 * Horizontal row that keeps the focused card in the leading slot.
 * One press eases. A held key uses a short linear step so the row glides
 * instead of restarting the ease on every repeat. The focused card lifts slightly.
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
    onFocusedIndex: ((Int) -> Unit)? = null,
    onExitDown: (() -> Unit)? = null,
    onExitUp: (() -> Unit)? = null,
    initialWindowStart: Int = 0,
    initialIndex: Int = -1,
    forceFocused: Boolean = false,
    imageUrls: List<String> = emptyList(),
    backdropUrls: List<String> = emptyList(),
    prefetchRole: ArtRole = ArtRole.Primary,
    caption: @Composable (BaseItemDto) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    onTrailing: (() -> Unit)? = null,
    art: @Composable BoxScope.(BaseItemDto) -> Unit,
) {
    val extra = if (trailing != null) 1 else 0
    val total = items.size + extra
    if (total == 0) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stride = cardWidth + HomeStyle.rowGap
        val usable = maxWidth - HomeStyle.gutter
        val fullSlots = ((usable + HomeStyle.rowGap) / stride).toInt().coerceAtLeast(1)
        val maxStart = (total - fullSlots).coerceAtLeast(0)
        val startWindow = if (initialIndex >= 0) {
            initialIndex.coerceIn(0, maxStart)
        } else {
            initialWindowStart.coerceIn(0, maxStart)
        }
        val startSlot = if (initialIndex >= 0) (initialIndex - startWindow).coerceAtLeast(0) else 0
        var windowStart by remember(total, fullSlots) { mutableIntStateOf(startWindow) }
        var slot by remember(total) { mutableIntStateOf(startSlot) }
        var rowFocused by remember { mutableStateOf(forceFocused) }
        var repeating by remember { mutableStateOf(false) }
        var lastMove by remember { mutableLongStateOf(0L) }
        if (windowStart > maxStart) windowStart = maxStart
        val lastSlot = (min(fullSlots, total - windowStart) - 1).coerceAtLeast(0)
        if (slot > lastSlot) slot = lastSlot
        val soundView = androidx.compose.ui.platform.LocalView.current

        fun noteRepeat() {
            val now = System.currentTimeMillis()
            repeating = now - lastMove < 160L
            lastMove = now
        }

        fun moveRight() {
            val before = windowStart to slot
            val moreRight = total - windowStart - fullSlots
            val endSlot = (min(fullSlots, total - windowStart) - 1).coerceAtLeast(0)
            if (slot == 0 && moreRight > 0) windowStart++ else if (slot < endSlot) slot++
            if (before != windowStart to slot) playNavigationSound(soundView)
        }

        fun moveLeft() {
            val before = windowStart to slot
            if (slot > 0) slot-- else if (windowStart > 0) windowStart--
            if (before != windowStart to slot) playNavigationSound(soundView)
        }

        val density = LocalDensity.current
        val headroom = with(density) {
            val needed = FocusLift.topOverflowPx(
                cardHeight.roundToPx(),
                FocusLift.rise.roundToPx(),
                HomeStyle.focusStroke.roundToPx(),
            )
            val reserved = (FocusLift.rise + 10.dp).roundToPx()
            kotlin.math.max(needed, reserved).toDp()
        }
        val insetPx = with(density) { HomeStyle.gutter.roundToPx() }
        val stridePx = with(density) { stride.roundToPx() }
        val slideSpec: androidx.compose.animation.core.FiniteAnimationSpec<Dp> =
            if (repeating) Motion.repeatStep() else Motion.row()
        val slide by animateDpAsState(
            with(density) { LeadingPeek.slide(windowStart, insetPx, stridePx).toDp() },
            slideSpec,
            label = "leading-slot-slide",
        )
        val shape = RoundedCornerShape(corner)
        val focusedIndex = (windowStart + slot).coerceIn(0, total - 1)
        val prefetchContext = LocalContext.current
        val pageResumed = com.xxxx.emby_tv.ui.home.rememberPageResumed()
        LaunchedEffect(focusedIndex, rowFocused) {
            if (rowFocused || forceFocused) onFocusedIndex?.invoke(focusedIndex)
        }
        LaunchedEffect(windowStart, focusedIndex, imageUrls, backdropUrls, pageResumed) {
            if (!com.xxxx.emby_tv.ui.home.PageWork.runs(pageResumed)) return@LaunchedEffect
            val jobs = mutableListOf<coil3.request.Disposable>()
            if (imageUrls.isNotEmpty()) {
                val end = (windowStart + fullSlots + 4).coerceAtMost(imageUrls.size)
                val start = windowStart.coerceIn(0, imageUrls.size)
                jobs += ArtworkPrefetch.enqueue(prefetchContext, imageUrls.subList(start, end), prefetchRole)
            }
            if (backdropUrls.isNotEmpty()) {
                jobs += ArtworkPrefetch.enqueueBackdrops(
                    prefetchContext,
                    listOfNotNull(backdropUrls.getOrNull(focusedIndex), backdropUrls.getOrNull(focusedIndex + 1)),
                )
            }
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                jobs.forEach { it.dispose() }
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
                .navigationSound()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> if (onExitDown != null) { onExitDown(); true } else false
                        Key.DirectionUp -> if (onExitUp != null) { onExitUp(); true } else false
                        Key.DirectionRight -> { noteRepeat(); moveRight(); true }
                        Key.DirectionLeft -> if (slot == 0 && windowStart == 0) false else { noteRepeat(); moveLeft(); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (LibraryActivation.opensOn(event.type, event.key)) {
                                if (focusedIndex >= items.size) onTrailing?.invoke() else onActivate(items[focusedIndex])
                            }
                            true
                        }
                        Key.Menu, Key.Bookmark -> { onMenu(); true }
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .padding(top = headroom),
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .offset(x = slide),
                    horizontalArrangement = Arrangement.spacedBy(HomeStyle.rowGap),
                ) {
                    items.forEachIndexed { index, item ->
                        val active = (rowFocused || forceFocused) && index == focusedIndex
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FocusCard(
                                modifier = Modifier
                                    .width(cardWidth)
                                    .height(cardHeight)
                                    .focusProperties { canFocus = false }
                                    .clickable { onActivate(item) },
                                focused = active,
                                shape = shape,
                                lift = true,
                            ) { art(item) }
                            caption(item)
                        }
                    }
                    if (trailing != null) {
                        val active = (rowFocused || forceFocused) && focusedIndex == items.size
                        FocusCard(
                            modifier = Modifier
                                .width(cardWidth)
                                .height(cardHeight)
                                .clickable { onTrailing?.invoke() },
                            focused = active,
                            shape = shape,
                            lift = true,
                        ) { trailing() }
                    }
                }
            }
        }
    }
}
