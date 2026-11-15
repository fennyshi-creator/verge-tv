package com.xxxx.emby_tv.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.remote.personWorksPath
import com.xxxx.emby_tv.ui.library.LibraryActivation
import com.xxxx.emby_tv.ui.motion.Motion
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release261Test {
    @Test
    fun episodeSeriesCardKeepsTheSeriesPoster() {
        val episode = BaseItemDto(
            id = "ep",
            name = "S1:E2-不再有令人不快的意外",
            type = "Episode",
            seriesId = "series",
            seriesName = "律界战争",
            seriesPrimaryImageTag = "series-primary",
            productionYear = 2024,
        )
        val series = BaseItemDto(
            id = "kept",
            name = "已有剧集",
            type = "Series",
            imageTags = mapOf("Primary" to "own-poster"),
        )
        val (_, shows) = EmbyPresentation.personWorks(listOf(episode, series))
        val collapsed = shows.first { it.id == "series" }
        assertEquals("律界战争", collapsed.name)
        assertEquals("Series", collapsed.type)
        assertEquals("series-primary", collapsed.imageTags?.get("Primary"))
        assertEquals("own-poster", shows.first { it.id == "kept" }.imageTags?.get("Primary"))
        assertTrue(personWorksPath("u", "p", "k").contains("SeriesPrimaryImageTag"))
    }

    @Test
    fun collectionOkOpensOnKeyDownOnly() {
        assertTrue(LibraryActivation.opensOn(KeyEventType.KeyDown, Key.DirectionCenter))
        assertTrue(LibraryActivation.opensOn(KeyEventType.KeyDown, Key.Enter))
        assertFalse(LibraryActivation.opensOn(KeyEventType.KeyUp, Key.DirectionCenter))
        assertFalse(LibraryActivation.opensOn(KeyEventType.KeyDown, Key.DirectionDown))
    }

    @Test
    fun sharedMotionIsALittleSlowerAndRepeatStaysShort() {
        assertTrue(Motion.focusMs in 220..280)
        assertTrue(Motion.rowMs in 320..380)
        assertTrue(Motion.pageMs in 360..420)
        assertEquals(90, Motion.repeatStep<Float>().durationMillis)
        assertTrue(Motion.focus<Int>().durationMillis == Motion.focusMs)
        assertTrue(EmbyPresentation.versionShowsLabel(true))
        assertFalse(EmbyPresentation.versionShowsLabel(false))
    }
}
