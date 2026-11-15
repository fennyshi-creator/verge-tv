package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.remote.favoriteItemsPath
import com.xxxx.emby_tv.ui.connect.ConnectForm
import com.xxxx.emby_tv.ui.connect.PairingPage
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release259Test {
    @Test
    fun stillStopsAtTheHeroAndTheSeamIsShort() {
        val hero = 900
        val seam = HomeMask.seam.value.toInt()
        val bands = HomeMask.bands(hero, seam)
        assertTrue(HomeMask.seam.value in 80f..160f)
        assertEquals(hero, bands.stillBottom)
        assertEquals(hero, bands.opaqueTop)
        assertEquals(hero - seam, bands.seamTop)
        assertEquals(0f, HomeMask.stillAlpha(1f))
        assertFalse(HomeMask.paintsOwnFill("continue"))
        assertTrue(HomeMask.sharesWash("continue"))
        assertTrue(HomeMask.sharesWash("library"))
        assertFalse(HomeMask.stillBehindContinue(bands, continueTitleTop = hero))
        assertTrue(HomeMask.opaqueFromContinue(bands, continueTitleTop = hero))
        assertTrue(HomeMask.emptyHistoryMeetsLibrary(bands, libraryTop = hero))
        assertTrue(HomeMask.stillBehindContinue(bands, continueTitleTop = hero - 1))
    }

    @Test
    fun heroTitleShiftDoesNotMoveContinueWatching() {
        assertTrue(HomeMask.shiftDp("hero-title") in 24f..56f)
        assertEquals(0f, HomeMask.shiftDp("continue"))
        assertEquals(0f, HomeMask.shiftDp("library"))
    }

    @Test
    fun favoritesCoverMoviesAndSeriesInEveryLibrary() {
        val path = favoriteItemsPath("user", "token")
        assertTrue(path.contains("IncludeItemTypes=Movie,Series,Episode"))
        assertTrue(path.contains("Filters=IsFavorite"))
        assertTrue(path.contains("Recursive=true"))
        assertFalse(path.contains("ParentId="))
        val movie = BaseItemDto(id = "m", name = "电影", type = "Movie", productionYear = 2020)
        val series = BaseItemDto(id = "s", name = "剧集", type = "Series", productionYear = 2019)
        val episode = BaseItemDto(
            id = "e",
            name = "第1集",
            type = "Episode",
            seriesId = "s2",
            seriesName = "另一部剧",
            productionYear = 2024,
        )
        val duplicate = BaseItemDto(
            id = "e2",
            name = "第2集",
            type = "Episode",
            seriesId = "s",
            seriesName = "剧集",
        )
        val shown = EmbyPresentation.favoritesForDisplay(listOf(movie, series, episode, duplicate))
        assertEquals(listOf("m", "s", "s2"), shown.map { it.id })
        assertEquals("Series", shown.first { it.id == "s2" }.type)
        assertEquals("另一部剧", shown.first { it.id == "s2" }.name)
        assertEquals("收藏影片/剧集", EmbyPresentation.FAVORITES_TITLE)
        val byYear = EmbyPresentation.sortFavorites(shown, byYear = true)
        assertEquals("s2", byYear.first().id)
    }

    @Test
    fun connectScreenAndPairingPageAreNotPurple() {
        assertEquals("443", ConnectForm.portFor("https", "8096"))
        assertEquals("8096", ConnectForm.portFor("http", "443"))
        assertEquals("8920", ConnectForm.portFor("https", "8920"))
        assertEquals(0.42f, ConnectForm.LEFT_FRACTION)
        val css = PairingPage.css()
        assertTrue(css.contains(PairingPage.background))
        assertTrue(css.contains(PairingPage.buttonBackground))
        assertTrue(css.contains("#141210") || css.contains(PairingPage.buttonText))
        assertFalse(PairingPage.usesLegacyPurple(css))
        assertFalse(css.contains("var(--theme-primary)"))
    }
}
