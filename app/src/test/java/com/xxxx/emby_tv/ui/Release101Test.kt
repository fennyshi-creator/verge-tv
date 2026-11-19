package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.home.PageWork
import com.xxxx.emby_tv.ui.motion.LibraryWindow
import com.xxxx.emby_tv.ui.motion.StageScrollPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Release101Test {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun singleTapScrollsEasedAndOnlyARealRepeatSteps() {
        assertFalse(StageScrollPolicy.shortStep(nowMs = 1_000, lastAtMs = 900, armedRepeat = false))
        assertTrue(StageScrollPolicy.shortStep(nowMs = 1_000, lastAtMs = 900, armedRepeat = true))
        assertTrue(StageScrollPolicy.shortStep(nowMs = 1_000, lastAtMs = 900, armedRepeat = null))
        assertFalse(StageScrollPolicy.shortStep(nowMs = 1_000, lastAtMs = 700, armedRepeat = null))
        val library = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/library/LibraryNav.kt").readText()
        val down = library.substringAfter("Key.DirectionDown ->").substringBefore("Key.DirectionUp ->")
        val up = library.substringAfter("Key.DirectionUp ->").substringBefore("Key.DirectionLeft")
        assertTrue(down.contains("onStep(1, repeat)"))
        assertFalse(down.contains("requestFocus()"))
        assertTrue(up.contains("onStep(-1, repeat)"))
        assertTrue(library.contains("key(index)"))
    }

    @Test
    fun libraryComposesAWindowAroundTheFocusedRow() {
        assertEquals(0..3, LibraryWindow.range(focused = 0, total = 20))
        assertEquals(3..8, LibraryWindow.range(focused = 5, total = 20))
        assertEquals(17..19, LibraryWindow.range(focused = 19, total = 20))
        assertEquals(IntRange.EMPTY, LibraryWindow.range(focused = 0, total = 0))
        val stride = LibraryWindow.stridePx(gridWidthPx = 1920, density = 2f, fontScale = 1f)
        assertTrue(stride > 200)
        val library = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/library/LibraryGridPage.kt").readText()
        assertTrue(library.contains("LibraryKeyedWindow"))
        assertFalse(library.contains("rows.forEachIndexed"))
    }

    @Test
    fun playerDoesNotDrawTheDebugOverlay() {
        val player = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/PlayerScreen.kt").readText()
        assertFalse(player.contains("PlaybackDebug.lines"))
        assertTrue(player.contains("ratingAlpha"))
        assertTrue(player.contains("player.release()"))
        assertFalse(PageWork.runs(resumed = false))
        assertTrue(PageWork.runs(resumed = true))
    }
}
