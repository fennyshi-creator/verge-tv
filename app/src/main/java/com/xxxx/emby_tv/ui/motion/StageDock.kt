package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Homepage vertical motion. The focused row docks at a fixed stage Y.
 * It is not "scroll the smallest amount that reveals the row".
 * Index 0 is the top of the page (the hero) and always returns to scroll 0.
 */
object StageDock {
    const val MIN_TITLE_SCALE = 0.68f

    /** How much of the next details block may show under the hero. A sliver, not a row. */
    const val FIRST_PEEK_DP = 40

    /**
     * Movie first screen. The cast label is about 60dp, then the circles.
     * This reaches the tops of those circles and stops before a whole head.
     */
    const val CAST_HEAD_PEEK_DP = 78

    fun heroBlockPx(viewportPx: Int, peekPx: Int): Int = (viewportPx - peekPx).coerceAtLeast(0)

    /** The first screen fails if the peek is already a large part of the next block. */
    fun peekIsSliver(peekPx: Int, nextBlockPx: Int): Boolean =
        nextBlockPx > 0 && peekPx * 3 < nextBlockPx

    /**
     * Details and the library grid must not let the default bring-into-view
     * scroll. That pivot is what made 2.0.39 creep like a document while
     * StageDock tried to dock the same column.
     */
    fun bringIntoViewDistance(): Float = 0f

    /** Collections insert above 更多类似的. Hero and cast stay put; that row and below move. */
    fun shiftsWhenCollectionsArrive(focusKey: String): Boolean =
        focusKey == "similar" || focusKey.startsWith("box:")

    /** Keep the focused row at the same screen Y after content above it grows. */
    fun keptScroll(scroll: Int, oldTop: Int, newTop: Int, maxScroll: Int = Int.MAX_VALUE): Int {
        if (oldTop < 0 || newTop < 0) return scroll.coerceAtLeast(0)
        return (scroll + (newTop - oldTop)).coerceIn(0, maxScroll.coerceAtLeast(0))
    }

    fun offset(rowTop: Int, index: Int, stagePx: Int, maxScroll: Int): Int {
        if (index <= 0) return 0
        return (rowTop - stagePx).coerceIn(0, maxScroll.coerceAtLeast(0))
    }

    /** Space below the last row so that row can sit on the stage instead of the bottom. */
    fun bottomSpacer(viewportPx: Int, stagePx: Int): Int {
        return (viewportPx - stagePx).coerceAtLeast(0)
    }

    fun titleScale(distancePx: Int, spanPx: Int, minScale: Float = MIN_TITLE_SCALE): Float {
        if (spanPx <= 0) return 1f
        val t = 1f - (abs(distancePx).toFloat() / spanPx.toFloat()).coerceIn(0f, 1f)
        return minScale + (1f - minScale) * t
    }

    fun travel(fromTop: Int, toTop: Int): Int = toTop - fromTop

    /** First library row stays under the title. Later rows dock at the stage. */
    fun libraryRowOffset(rowTop: Int, gridRow: Int, stagePx: Int, maxScroll: Int): Int {
        if (gridRow <= 0) return 0
        return offset(rowTop, gridRow, stagePx, maxScroll)
    }
}

@Stable
class StageScroll(
    private val scroll: ScrollState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var stagePx: Int = 0
    val tops: SnapshotStateList<Int> = mutableStateListOf()
    private var job: kotlinx.coroutines.Job? = null
    private var lastAt = 0L
    private var activeIndex = 0

    fun record(index: Int, contentY: Int) {
        if (index < 0) return
        while (tops.size <= index) tops.add(0)
        if (abs(tops[index] - contentY) > 2) tops[index] = contentY
    }

    fun target(index: Int): Int = StageDock.offset(
        rowTop = tops.getOrElse(index) { 0 },
        index = index,
        stagePx = stagePx,
        maxScroll = scroll.maxValue,
    )

    fun dock(index: Int) {
        dockTo(index, target(index))
    }

    /** Same as [dock], but never stops above [minScroll]. Used to clear the hero still. */
    fun dockAtLeast(index: Int, minScroll: Int) {
        val limit = scroll.maxValue.coerceAtLeast(0)
        val dest = maxOf(target(index), minScroll.coerceAtLeast(0)).coerceAtMost(limit)
        dockTo(index, dest)
    }

    private fun dockTo(index: Int, dest: Int) {
        val now = System.currentTimeMillis()
        val repeating = now - lastAt in 1..159
        lastAt = now
        if (activeIndex == index && job?.isActive == true) return
        if (activeIndex == index && scroll.value == dest) return
        activeIndex = index
        job?.cancel()
        val spec: AnimationSpec<Float> = if (repeating) Motion.repeatStep() else Motion.page()
        job = scope.launch {
            scroll.animateScrollTo(dest, spec)
        }
    }

    fun jump(index: Int) {
        activeIndex = index
        job?.cancel()
        val dest = target(index)
        job = scope.launch { scroll.scrollTo(dest) }
    }
}

@Composable
fun rememberStageScroll(scroll: ScrollState): StageScroll {
    val scope = rememberCoroutineScope()
    return remember(scroll) { StageScroll(scroll, scope) }
}

/** The page scrolls only through [StageScroll]. Bring-into-view contributes nothing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideStageScroll(content: @Composable () -> Unit) {
    val spec = remember {
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> = Motion.pageFloat
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                return StageDock.bringIntoViewDistance()
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

private class InsertHold {
    var token: String = ""
    var index: Int = 0
    var key: String = "hero"
    var frozenTop: Int = -1
}

/**
 * When collection rows appear above the focused row, add the same delta to the
 * scroll so that row stays at the same screen Y.
 */
@Composable
fun HoldStageWhenRowsArrive(
    scroll: ScrollState,
    stage: StageScroll,
    token: String,
    focusKey: String,
    focusIndex: Int,
) {
    val hold = remember { InsertHold() }
    if (token != hold.token) {
        hold.frozenTop = if (StageDock.shiftsWhenCollectionsArrive(hold.key)) {
            stage.tops.getOrElse(hold.index) { -1 }
        } else {
            -1
        }
        hold.token = token
    }
    hold.index = focusIndex
    hold.key = focusKey
    LaunchedEffect(token) {
        val oldTop = hold.frozenTop
        if (oldTop < 0 || focusIndex < 0) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        val newTop = stage.tops.getOrElse(focusIndex) { -1 }
        if (newTop < 0) return@LaunchedEffect
        scroll.scrollTo(StageDock.keptScroll(scroll.value, oldTop, newTop, scroll.maxValue))
    }
}
