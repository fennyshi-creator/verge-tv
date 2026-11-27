package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.components.ExitKey
import com.xxxx.emby_tv.ui.home.HomeHeroCopy
import com.xxxx.emby_tv.ui.home.HomeMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

class HomeUiFixTest {
    private fun projectFile(relative: String): File {
        val direct = File(relative)
        if (direct.isFile) return direct
        val fromModule = File("../$relative")
        if (fromModule.isFile) return fromModule
        return direct
    }

    @Test
    fun exitChoiceRunsOnTheFirstCenterPress() {
        assertTrue(ExitKey.activates(KeyEventType.KeyDown, Key.DirectionCenter))
        assertTrue(ExitKey.activates(KeyEventType.KeyDown, Key.Enter))
        assertTrue(ExitKey.activates(KeyEventType.KeyDown, Key.NumPadEnter))
        assertFalse(ExitKey.activates(KeyEventType.KeyUp, Key.DirectionCenter))
        assertFalse(ExitKey.activates(KeyEventType.KeyUp, Key.Enter))
        assertFalse(ExitKey.activates(KeyEventType.KeyDown, Key.DirectionCenter, repeat = true))
        assertFalse(ExitKey.activates(KeyEventType.KeyDown, Key.DirectionLeft))

        val source = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/components/HomeExitPrompt.kt").readText()
        assertTrue(source.contains("continueFocus.requestFocus()"))
        assertTrue(source.contains("onPreviewKeyEvent"))
        assertTrue(source.contains("ExitKey.activates"))
        assertFalse(source.contains(".focusable()"))
        assertTrue(source.contains(".clickable(onClick = onClick)"))
        val choice = source.substringAfter("private fun ExitChoice")
        assertTrue(choice.indexOf("onPreviewKeyEvent") < choice.indexOf(".clickable"))
    }

    @Test
    fun heroSideShadeIsSlightAndLeavesTheRightSideBright() {
        assertFalse(HomeMask.horizontalHeroCutoff())
        assertTrue(HomeMask.HERO_SHADE_LEFT < 0.72f)
        assertTrue(HomeMask.heroSideAlpha(0f) > 0.28f)
        assertTrue(HomeMask.heroSideAlpha(0f) < 0.72f)
        assertEquals(HomeMask.HERO_SHADE_LEFT, HomeMask.heroSideAlpha(0f))
        assertTrue(HomeMask.heroSideAlpha(0.32f) > 0.08f)
        assertTrue(HomeMask.heroSideAlpha(0.32f) < HomeMask.heroSideAlpha(0f))
        assertEquals(0f, HomeMask.heroSideAlpha(0.60f))
        assertEquals(0f, HomeMask.heroSideAlpha(1f))
        var previous = 1f
        for (step in 0..20) {
            val alpha = HomeMask.heroSideAlpha(step / 20f)
            assertTrue("shade rose at ${step / 20f}", alpha <= previous + 0.001f)
            previous = alpha
        }
        assertEquals(0f, HomeMask.firstScreenDarken(0f))
        assertEquals(1f, HomeMask.firstScreenDarken(1f))

        val home = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/home/OfficialHome.kt").readText()
        val pinned = home.substringAfter("pinnedBackdrop.isNotBlank()").substringBefore("AmbientWash")
        assertTrue(pinned.contains("HeroSideShade"))
        assertTrue(pinned.contains("HeroBottomScrim"))
        assertTrue(pinned.indexOf("HeroSideShade") < pinned.indexOf("HeroBottomScrim"))
    }

    @Test
    fun heroCopyIsCompactAndStaysInTheShadedColumn() {
        assertTrue(HomeHeroCopy.TITLE_SP < 28)
        assertTrue(HomeHeroCopy.TITLE_SP >= 22)
        assertTrue(HomeHeroCopy.OVERVIEW_SP < 15)
        assertEquals(2, HomeHeroCopy.OVERVIEW_LINES)
        assertTrue(HomeHeroCopy.COLUMN_FRACTION < 0.50f)
        assertTrue(HomeHeroCopy.COLUMN_FRACTION > 0.34f)
        assertTrue(HomeHeroCopy.LOGO_DP < 68)
        val clearAt = HomeMask.heroSideStops.first { it.second == 0f }.first
        assertTrue(HomeHeroCopy.COLUMN_FRACTION < clearAt)

        val home = projectFile("app/src/main/java/com/xxxx/emby_tv/ui/home/OfficialHome.kt").readText()
        assertTrue(home.contains("HomeHeroCopy.TITLE_SP"))
        assertTrue(home.contains("HomeHeroCopy.OVERVIEW_SP"))
        assertTrue(home.contains("HomeHeroCopy.OVERVIEW_LINES"))
        assertTrue(home.contains("HomeHeroCopy.COLUMN_FRACTION"))
        assertTrue(home.contains("HomeHeroCopy.LOGO_DP"))
        assertFalse(home.contains("fontSize = 28.sp"))
        assertFalse(home.contains("maxLines = 3"))
    }
}
