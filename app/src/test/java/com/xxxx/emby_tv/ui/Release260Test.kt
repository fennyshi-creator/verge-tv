package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.remote.boxSetItemsPath
import com.xxxx.emby_tv.data.remote.personWorksPath
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release260Test {
    @Test
    fun continueAndLibraryShareOneWash() {
        assertFalse(HomeMask.paintsOwnFill("continue"))
        assertFalse(HomeMask.paintsOwnFill("library"))
        assertFalse(HomeMask.paintsOwnFill("posters"))
        assertTrue(HomeMask.sharesWash("continue"))
        assertTrue(HomeMask.sharesWash("library"))
        assertTrue(HomeMask.sharesWash("posters"))
        assertEquals(0f, HomeMask.stillAlpha(1f))
        assertEquals(1f, HomeMask.stillAlpha(0f))
        val hero = 900
        val seam = 120
        assertEquals(hero, HomeMask.bands(hero, seam).stillBottom)
        assertTrue(HomeMask.stillKeepUntil(hero, seam) < 1f)
        assertEquals(0f, HomeMask.shiftDp("continue"))
    }

    @Test
    fun boxSetLibraryOpensTheCollectionListThenACollection() {
        val library = BaseItemDto(id = "lib", name = "专题影单", type = "CollectionFolder", collectionType = "boxsets")
        assertEquals("collections", EmbyPresentation.libraryDestination(library))
        assertEquals("browse", EmbyPresentation.libraryDestination(
            BaseItemDto(collectionType = "movies"),
        ))
        val box = BaseItemDto(id = "box", name = "满满的文学气息", type = "BoxSet")
        assertEquals("collection/box", EmbyPresentation.detailRoute(box))
        val movie = BaseItemDto(id = "m", type = "Movie")
        assertEquals("media/m", EmbyPresentation.detailRoute(movie))
        val path = boxSetItemsPath("user", "box", "token", limit = 80)
        assertTrue(path.contains("ParentId=box"))
        assertTrue(path.contains("IncludeItemTypes=Movie,Series"))
        assertTrue(path.contains("Limit=80"))
        assertEquals("剧情 / 爱情 · PG", EmbyPresentation.collectionMetaLine(listOf("剧情", "爱情"), "PG"))
        assertEquals("", EmbyPresentation.collectionMetaLine(emptyList(), null))
        val episode = BaseItemDto(id = "e", type = "Episode", seriesId = "s", seriesName = "剧集")
        val shown = EmbyPresentation.collectionChildrenForDisplay(listOf(movie, episode))
        assertEquals(listOf("m", "s"), shown.map { it.id })
        assertEquals("Series", shown.last().type)
    }

    @Test
    fun personPageCollapsesEpisodesAndOmitsMissingFacts() {
        val movie = BaseItemDto(id = "m", name = "影后", type = "Movie", productionYear = 2024)
        val episode = BaseItemDto(
            id = "e",
            name = "S1:E2",
            type = "Episode",
            seriesId = "s",
            seriesName = "我们与恶的距离",
            productionYear = 2019,
            seriesPrimaryImageTag = "series-poster",
        )
        val series = BaseItemDto(id = "s2", name = "饮食男女", type = "Series", productionYear = 1994)
        val (movies, shows) = EmbyPresentation.personWorks(listOf(movie, episode, series))
        assertEquals(listOf("影后"), movies.map { it.name })
        assertEquals(setOf("我们与恶的距离", "饮食男女"), shows.map { it.name }.toSet())
        assertTrue(shows.none { it.name == "S1:E2" })
        val collapsed = shows.first { it.id == "s" }
        assertEquals("series-poster", collapsed.seriesPrimaryImageTag)
        assertEquals("series-poster", collapsed.imageTags?.get("Primary"))
        assertEquals("series/s", EmbyPresentation.detailRoute(shows.first { it.id == "s" }))
        assertEquals("media/m", EmbyPresentation.detailRoute(movies.first()))
        val line = EmbyPresentation.personFactLine(
            "1959-09-05T00:00:00.0000000Z",
            null,
            listOf("Taipei, Taiwan"),
            todayYear = 2026,
            todayMonth = 11,
            todayDay = 15,
        )
        assertTrue(line.startsWith("1959年9月5日"))
        assertTrue(line.contains("岁"))
        assertTrue(line.endsWith("Taipei, Taiwan"))
        assertEquals("Taipei, Taiwan", EmbyPresentation.personFactLine(null, null, listOf("Taipei, Taiwan"), 2026, 1, 1))
        assertEquals("", EmbyPresentation.personFactLine(null, null, null, 2026, 1, 1))
        val works = personWorksPath("user", "p", "token")
        assertTrue(works.contains("PersonIds=p"))
        assertTrue(works.contains("IncludeItemTypes=Movie,Series,Episode"))
        assertFalse(works.contains("ParentId="))
    }
}
