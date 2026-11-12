package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.EmbyResponseDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.UserDataDto
import com.xxxx.emby_tv.data.remote.EmbyJson
import com.xxxx.emby_tv.data.remote.similarItemsPath
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyPresentationTest {
    private val gson = EmbyJson.gson

    private fun fixture(name: String): com.google.gson.JsonObject {
        val stream = checkNotNull(javaClass.classLoader).getResourceAsStream("fixtures/$name")
            ?: error("missing fixture $name")
        return stream.use { gson.fromJson(it.reader(), com.google.gson.JsonObject::class.java) }
    }

    private fun item(json: com.google.gson.JsonElement): BaseItemDto =
        gson.fromJson(json, BaseItemDto::class.java)

    @Test
    fun odysseyMovieMatchesLiveLibrary() {
        val root = fixture("emby-ui-fixtures.json")
        val movie = item(root.getAsJsonObject("movie"))
        assertEquals("奥德赛", EmbyPresentation.displayName(movie))
        assertEquals("奥德赛", movie.name)
        assertTrue(EmbyPresentation.hasLogo(movie))
        assertEquals("2026", EmbyPresentation.yearLabel(movie))
        assertEquals("冒险 / 动作 / 奇幻", EmbyPresentation.genresLine(movie))
        assertEquals("R", movie.officialRating)
        assertEquals("★8.0", EmbyPresentation.starLabel(movie.communityRating))
        assertEquals("续看 · 还剩 2h 42min", EmbyPresentation.playActionLabel(movie))
        assertTrue(movie.userData?.played == true)
        assertEquals("4K", EmbyPresentation.qualityBadge(movie))
        assertEquals(listOf("4K", "1080p", "其他版本"), movie.mediaSources.orEmpty().map { EmbyPresentation.versionLabel(it) })
        val lead = movie.people.orEmpty().first()
        assertEquals("马特·达蒙", lead.name)
        assertEquals("Odysseus", lead.role)
        val similarType = object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type
        val similar: EmbyResponseDto<BaseItemDto> = gson.fromJson(root.get("movie_similar"), similarType)
        assertEquals("木乃伊2：木乃伊归来", similar.items.first().name)
        assertEquals("2001", EmbyPresentation.yearLabel(similar.items.first()))
        val logo = EmbyPresentation.logoUrl("https://example.test", movie)
        assertTrue(logo.contains("/Images/Logo"))
        assertTrue(logo.contains("tag=${movie.imageTags?.get("Logo")}"))
        assertTrue(logo.contains("maxWidth="))
        assertTrue(logo.contains("quality="))
    }

    @Test
    fun tulsaKingSeriesMatchesLiveLibrary() {
        val root = fixture("emby-ui-fixtures-tulsa.json")
        val series = item(root.getAsJsonObject("series"))
        assertEquals("塔尔萨之王", EmbyPresentation.displayName(series))
        assertFalse(EmbyPresentation.displayName(series) == series.originalTitle)
        assertTrue(EmbyPresentation.hasLogo(series))
        assertEquals("犯罪 / 剧情", EmbyPresentation.genresLine(series))
        assertEquals("TV-MA", series.officialRating)
        assertEquals("2022 - 现在", EmbyPresentation.yearLabel(series))
        assertEquals("★8.3", EmbyPresentation.starLabel(series.communityRating))
        val seasons: EmbyResponseDto<BaseItemDto> = gson.fromJson(
            root.get("seasons"),
            object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type,
        )
        assertEquals(listOf("第 1 季", "第 2 季", "第 3 季", "第 4 季"), seasons.items.map { EmbyPresentation.seasonTabLabel(it) })
        val nextUp: EmbyResponseDto<BaseItemDto> = gson.fromJson(
            root.get("next_up"),
            object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type,
        )
        val episode = nextUp.items.first()
        assertEquals("S2:E7 - 生存支援", EmbyPresentation.episodeHeading(episode))
        assertEquals("续看 · 还剩 11min", EmbyPresentation.playActionLabel(episode))
        assertEquals("40min", EmbyPresentation.runtimeLabel(episode.runTimeTicks))
        assertNull(EmbyPresentation.qualityBadge(episode))
        assertEquals("西尔维斯特·史泰龙", series.people.orEmpty().first().name)
        val similar: EmbyResponseDto<BaseItemDto> = gson.fromJson(
            root.get("series_similar"),
            object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type,
        )
        val byName = similar.items.associateBy { it.name }
        assertEquals("2021 - 现在", EmbyPresentation.yearLabel(byName.getValue("金斯敦市长")))
        assertEquals("2022 - 2025", EmbyPresentation.yearLabel(byName.getValue("1923")))
        assertEquals("2021 - 2022", EmbyPresentation.yearLabel(byName.getValue("1883")))
    }

    @Test
    fun viewsAreTheThreeLiveLibraries() {
        val root = fixture("emby-ui-fixtures.json")
        val views: EmbyResponseDto<BaseItemDto> = gson.fromJson(
            root.get("views"),
            object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type,
        )
        assertEquals(listOf("电影Movies", "剧集Series", "专题影单"), views.items.map { it.name })
    }

    @Test
    fun resumeEpisodeUsesSeriesNameAndThumbOrPrimary() {
        val root = fixture("emby-ui-fixtures.json")
        val resume: EmbyResponseDto<BaseItemDto> = gson.fromJson(
            root.get("resume"),
            object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type,
        )
        val episode = resume.items.first { it.type == "Episode" }
        assertEquals("现在不是出轨的问题", EmbyPresentation.resumeCardTitle(episode))
        assertEquals("S1:E7 - 不速之客们", EmbyPresentation.resumeCardSubtitle(episode))
        val movie = resume.items.first()
        val thumb = EmbyPresentation.resumeImageUrl("https://example.test", movie)
        assertTrue(thumb.contains("/Images/Thumb"))
        assertTrue(thumb.contains("tag=${movie.imageTags?.get("Thumb")}"))
        val episodeStill = EmbyPresentation.resumeImageUrl("https://example.test", episode)
        assertTrue(episodeStill.contains("/Images/Primary"))
    }

    @Test
    fun similarQueryRequestsYearFields() {
        val path = similarItemsPath("user", "225365", 16, "token")
        val fields = path.substringAfter("Fields=").substringBefore("&")
        assertTrue(fields.startsWith("ProductionYear,EndDate,Status"))
        assertTrue(fields.contains("ImageTags"))
    }

    @Test
    fun playAndQualityEdgeCases() {
        val fresh = BaseItemDto(name = "新片", type = "Movie", runTimeTicks = 3_600_000_000_000L, userData = UserDataDto())
        assertEquals("播放", EmbyPresentation.playActionLabel(fresh))
        val unlabeled = BaseItemDto(
            name = "塔尔萨之王",
            type = "Episode",
            mediaSources = listOf(MediaSourceInfoDto(id = "1", name = "塔尔萨之王 - S02E07")),
        )
        assertNull(EmbyPresentation.qualityBadge(unlabeled))
        assertEquals("其他版本", EmbyPresentation.versionLabel(unlabeled.mediaSources!!.first()))
    }
}
