package com.xxxx.emby_tv.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Homepage vertical motion. The focused row docks at a fixed stage Y.
 * It is not "scroll the smallest amount that reveals the row".
 * Index 0 is the top of the page (the hero) and always returns to scroll 0.
 */
object StageDock {
    const val MIN_TITLE_SCALE = 0.68f

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
        val dest = target(index)
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
