package com.xxxx.emby_tv.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test
    fun tokensStayOnTheTvCurve() {
        assertEquals(200, Motion.focusMs)
        assertEquals(280, Motion.rowMs)
        assertEquals(300, Motion.pageMs)
        assertEquals(240, Motion.fadeMs)
        assertEquals(180, Motion.imageMs)
        assertEquals(90, Motion.repeatStep<Float>().durationMillis)
        assertEquals(Motion.rowMs, Motion.row<Float>().durationMillis)
        assertEquals(Motion.pageMs, Motion.page<Float>().durationMillis)
        assertEquals(Motion.fadeMs, Motion.fade<Float>().durationMillis)
    }
}
