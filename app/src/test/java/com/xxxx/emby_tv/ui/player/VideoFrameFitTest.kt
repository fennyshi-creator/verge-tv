package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameFitTest {
    @Test
    fun wideHdrLetterboxesOn1080pAnd2160p() {
        val hd = VideoFrameFit.place(1920, 1080, 3840, 1608, 1, 1, 0, VideoFrameFit.FIT)
        assertEquals(1920, hd.width)
        assertEquals(804, hd.height)
        assertEquals(0, hd.x)
        assertEquals(138, hd.y)

        val alt = VideoFrameFit.place(1920, 1080, 3840, 1600, 1, 1, 0, VideoFrameFit.FIT)
        assertEquals(800, alt.height)
        assertEquals(140, alt.y)

        val uhd = VideoFrameFit.place(3840, 2160, 3840, 1608, 1, 1, 0, VideoFrameFit.FIT)
        assertEquals(3840, uhd.width)
        assertEquals(1608, uhd.height)
        assertEquals(276, uhd.y)
    }

    @Test
    fun sixteenByNineFillsAndFourByThreePillars() {
        val full = VideoFrameFit.place(1920, 1080, 1920, 1080, 1, 1, 0, VideoFrameFit.FIT)
        assertEquals(0, full.x)
        assertEquals(0, full.y)
        assertEquals(1920, full.width)
        assertEquals(1080, full.height)

        val classic = VideoFrameFit.place(1920, 1080, 1440, 1080, 1, 1, 0, VideoFrameFit.FIT)
        assertEquals(1440, classic.width)
        assertEquals(1080, classic.height)
        assertEquals(240, classic.x)
        assertEquals(0, classic.y)
    }

    @Test
    fun anamorphicSarRotationAndFill() {
        val wide = VideoFrameFit.place(1920, 1080, 720, 480, 32, 27, 0, VideoFrameFit.FIT)
        assertEquals(1920, wide.width)
        assertEquals(1080, wide.height)

        val turned = VideoFrameFit.place(1920, 1080, 1920, 1080, 1, 1, 90, VideoFrameFit.FIT)
        assertTrue(turned.width < 1920)
        assertEquals(1080, turned.height)
        assertTrue(turned.x > 0)

        val fill = VideoFrameFit.place(1920, 1080, 3840, 1608, 1, 1, 0, VideoFrameFit.FILL)
        assertTrue(fill.width > 1920)
        assertEquals(1080, fill.height)

        val stretch = VideoFrameFit.place(1920, 1080, 3840, 1608, 1, 1, 0, VideoFrameFit.STRETCH)
        assertEquals(1920, stretch.width)
        assertEquals(1080, stretch.height)
    }
}
