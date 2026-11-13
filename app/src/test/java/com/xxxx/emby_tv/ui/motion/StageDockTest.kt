package com.xxxx.emby_tv.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StageDockTest {
    @Test
    fun heroReturnsToTheTrueTop() {
        assertEquals(0, StageDock.offset(rowTop = 0, index = 0, stagePx = 80, maxScroll = 2000))
        assertEquals(0, StageDock.offset(rowTop = 40, index = 0, stagePx = 80, maxScroll = 2000))
    }

    @Test
    fun focusedRowDocksAtTheStage() {
        val rowTop = 900
        val stage = 80
        assertEquals(rowTop - stage, StageDock.offset(rowTop, index = 2, stagePx = stage, maxScroll = 4000))
    }

    @Test
    fun lastRowUsesTheSameSlotWhenThePageCanScrollFarEnough() {
        val viewport = 1000
        val stage = 180
        val lastTop = 1400
        val lastHeight = 260
        val spacer = StageDock.bottomSpacer(viewport, stage)
        val maxScroll = lastTop + lastHeight + spacer - viewport
        val docked = StageDock.offset(lastTop, index = 4, stagePx = stage, maxScroll = maxScroll)
        assertEquals(lastTop - stage, docked)
        assertTrue(docked < maxScroll)
    }

    @Test
    fun lastRowClampsToTheBottomWithoutTheSpacer() {
        val viewport = 1000
        val stage = 180
        val lastTop = 1400
        val lastHeight = 200
        val maxScroll = lastTop + lastHeight - viewport
        val stuck = StageDock.offset(lastTop, index = 4, stagePx = stage, maxScroll = maxScroll)
        assertTrue(stuck < lastTop - stage)
        assertEquals(maxScroll, stuck)
    }

    @Test
    fun upFromTheLastRowTravelsOneRowSpan() {
        val tops = listOf(0, 640, 980, 1400)
        val step = StageDock.travel(tops[2], tops[3])
        val earlier = StageDock.travel(tops[1], tops[2])
        assertEquals(tops[3] - tops[2], step)
        assertEquals(tops[2] - tops[1], earlier)
        assertEquals(step, StageDock.travel(tops[2], tops[3]))
    }

    @Test
    fun titleIsFullSizeOnTheStageAndSmallerAwayFromIt() {
        val span = 400
        assertEquals(1f, StageDock.titleScale(0, span), 0.001f)
        assertEquals(StageDock.MIN_TITLE_SCALE, StageDock.titleScale(span, span), 0.001f)
        assertEquals(StageDock.MIN_TITLE_SCALE, StageDock.titleScale(span + 80, span), 0.001f)
        val mid = StageDock.titleScale(span / 2, span)
        assertTrue(mid > StageDock.MIN_TITLE_SCALE && mid < 1f)
    }
}
