package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.player.DolbyPassthrough
import com.xxxx.emby_tv.ui.player.PassthroughRoute
import com.xxxx.emby_tv.ui.player.PlaybackDebug
import com.xxxx.emby_tv.util.EmbyPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Release263Test {
    private val pcm = setOf(DolbyPassthrough.ENCODING_PCM_16BIT)

    @Test
    fun pcmOnlyHdmiDecodesEvenWhenDirectQueryIsTrue() {
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI, pcm, directQuery = true))
        assertTrue(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_HDMI, emptySet(), directQuery = false))
        assertTrue(
            PassthroughRoute.allowBitstream(
                PassthroughRoute.TYPE_HDMI,
                setOf(DolbyPassthrough.ENCODING_E_AC3),
                directQuery = false,
            ),
        )
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_BUILTIN_SPEAKER, emptySet(), true))
        assertFalse(PassthroughRoute.allowBitstream(PassthroughRoute.TYPE_WIRED_HEADPHONES, pcm, true))
        assertTrue(PassthroughRoute.allowActive(emptyList(), 0, directQuery = false))
    }

    @Test
    fun heroPoolUsesBackdropMoviesAndSeriesOnce() {
        val movie = BaseItemDto(
            id = "m1",
            name = "电影",
            type = "Movie",
            backdropImageTags = listOf("bd"),
        )
        val bare = BaseItemDto(id = "m2", name = "无图", type = "Movie")
        val series = BaseItemDto(
            id = "s1",
            name = "剧",
            type = "Series",
            backdropImageTags = listOf("bd2"),
        )
        val movies = BaseItemDto(
            id = "lib-m",
            collectionType = "movies",
            latestItems = listOf(movie, bare),
        )
        val shows = BaseItemDto(
            id = "lib-s",
            collectionType = "tvshows",
            latestItems = listOf(series),
        )
        val pool = EmbyPresentation.heroPool(listOf(movies, shows))
        assertEquals(listOf("m1", "s1"), pool.map { it.id })
        assertEquals("m1", EmbyPresentation.pickHero(pool, 0)?.id)
        assertEquals("s1", EmbyPresentation.pickHero(pool, 1)?.id)
        assertEquals(pool[0].id, EmbyPresentation.pickHero(pool, 0)?.id)
        assertNull(EmbyPresentation.pickHero(emptyList(), 0))
        assertEquals("primary", EmbyPresentation.libraryTileSource(hasPrimary = true, collageCount = 4))
        assertEquals("collage", EmbyPresentation.libraryTileSource(hasPrimary = false, collageCount = 4))
        assertEquals("placeholder", EmbyPresentation.libraryTileSource(hasPrimary = false, collageCount = 0))
    }

    @Test
    fun ratingFadesOnceAndSearchHintIsSeparate() {
        assertEquals("TV-MA", EmbyPresentation.playbackRating("TV-MA", "R", null))
        assertEquals("R", EmbyPresentation.playbackRating(null, "R", "PG"))
        assertEquals("PG", EmbyPresentation.playbackRating(" ", null, "PG"))
        assertNull(EmbyPresentation.playbackRating(null, null, null))
        assertEquals(0f, EmbyPresentation.ratingAlpha(-1))
        assertEquals(1f, EmbyPresentation.ratingAlpha(400))
        assertEquals(1f, EmbyPresentation.ratingAlpha(2000))
        assertEquals(0f, EmbyPresentation.ratingAlpha(5000))
        val login = com.xxxx.emby_tv.R.string.scan_qr_hint
        val search = com.xxxx.emby_tv.R.string.scan_qr_search_hint
        assertTrue(login != search)
    }

    @Test
    fun debugLinesNameTheRouteAndSdrRequest() {
        val lines = PlaybackDebug.lines(
            deviceType = PassthroughRoute.TYPE_HDMI,
            encodings = pcm,
            codec = "eac3",
            channels = 6,
            sampleRate = 48000,
            openedEncoding = 0,
            fellBack = false,
            forcedDecode = false,
            videoRange = "HDR",
            rangeType = "HDR10",
            transfer = null,
            videoCodec = "hevc",
            displayHdr = false,
            api = 31,
        )
        assertTrue(lines.any { it.contains("解码 PCM") })
        assertTrue(lines.any { it.contains("HDMI") })
        assertTrue(lines.any { it.contains("PCM") && it.contains("编码") })
        assertTrue(lines.any { it.contains("片源 HDR10") })
        assertTrue(lines.any { it.contains("仅SDR") })
        assertTrue(lines.any { it.contains("映射 SDR") })
        assertEquals("直出 HDR", PlaybackDebug.hdrOutput(sourceHdr = true, displayHdr = true, api = 31))
    }
}
