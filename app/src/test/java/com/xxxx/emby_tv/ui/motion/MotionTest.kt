package com.xxxx.emby_tv.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionTest {
    @Test
    fun tokensStayOnTheTvCurve() {
        assertEquals(240, Motion.focusMs)
        assertEquals(340, Motion.rowMs)
        assertEquals(380, Motion.pageMs)
        assertEquals(280, Motion.fadeMs)
        assertEquals(210, Motion.imageMs)
        assertEquals(90, Motion.repeatStep<Float>().durationMillis)
        assertEquals(Motion.rowMs, Motion.row<Float>().durationMillis)
        assertEquals(Motion.pageMs, Motion.page<Float>().durationMillis)
        assertEquals(Motion.fadeMs, Motion.fade<Float>().durationMillis)
    }
}
