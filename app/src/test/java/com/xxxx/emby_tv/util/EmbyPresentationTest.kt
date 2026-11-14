package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.EmbyResponseDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.UserDataDto
import com.xxxx.emby_tv.data.remote.EmbyJson
import com.xxxx.emby_tv.data.model.PersonInfo
import com.xxxx.emby_tv.data.remote.latestItemsPath
import com.xxxx.emby_tv.data.remote.seasonEpisodesPath
import com.xxxx.emby_tv.data.remote.seriesAddedPath
import com.xxxx.emby_tv.data.remote.libraryGenresPath
import com.xxxx.emby_tv.data.remote.libraryItemsPath
import com.xxxx.emby_tv.data.remote.recentlyPlayedSeriesPath
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
        assertEquals(listOf("Movie", "Series", "BoxSet"), views.items.map { EmbyPresentation.latestIncludeTypes(it) })
        val boxsets = views.items[2]
        assertEquals("24aa2a69b92cbe771992deda3385f7cb", boxsets.imageTags?.get("Primary"))
        val primary = EmbyPresentation.viewPrimaryImageUrl("https://example.test", boxsets)
        assertTrue(primary.contains("/Items/${boxsets.id}/Images/Primary"))
        assertTrue(primary.contains("tag=${boxsets.imageTags?.get("Primary")}"))
        val latest = latestItemsPath("user", "3", "Movie", 16, "token")
        assertTrue(latest.contains("IncludeItemTypes=Movie"))
        assertTrue(latest.contains("GroupItems=false"))
        assertTrue(latest.contains("/Items/Latest"))
        assertFalse(latest.contains("SortBy=DateCreated"))
        assertTrue(latestItemsPath("user", "237", "Series", 16, "token").contains("IncludeItemTypes=Series"))
        val grid = libraryItemsPath("user", "3", "Movie", 0, 36, "token")
        assertTrue(grid.contains("IncludeItemTypes=Movie"))
        assertTrue(grid.contains("SortBy=DateCreated"))
        assertTrue(grid.contains("SortOrder=Descending"))
        assertTrue(grid.contains("Recursive=true"))
        assertTrue(grid.contains("ParentId=3"))
        assertTrue(grid.contains("StartIndex=0"))
        assertTrue(grid.contains("Limit=36"))
        assertFalse(grid.contains("Genres="))
        val genreGrid = libraryItemsPath("user", "3", "Movie", 36, 36, "token", "动作")
        assertTrue(genreGrid.contains("Genres="))
        assertTrue(genreGrid.contains("StartIndex=36"))
        val genres = libraryGenresPath("user", "237", "Series", "token")
        assertTrue(genres.startsWith("/Genres?"))
        assertTrue(genres.contains("ParentId=237"))
        assertTrue(genres.contains("IncludeItemTypes=Series"))
        val played = recentlyPlayedSeriesPath("user", "237", 6, "token")
        assertTrue(played.contains("IncludeItemTypes=Series"))
        assertTrue(played.contains("SortBy=DatePlayed"))
        val seeds = EmbyPresentation.recentSeriesSeeds(
            resume = listOf(
                BaseItemDto(id = "ep", type = "Episode", seriesId = "s1", seriesName = "塔尔萨之王", name = "第 1 集"),
            ),
            played = listOf(BaseItemDto(id = "s2", type = "Series", name = "金斯敦市长")),
        )
        assertEquals(listOf("s1" to "塔尔萨之王", "s2" to "金斯敦市长"), seeds)
        val seriesAdded = seriesAddedPath("user", "237", 16, "token")
        assertTrue(seriesAdded.contains("/Users/user/Items?"))
        assertTrue(seriesAdded.contains("IncludeItemTypes=Series"))
        assertTrue(seriesAdded.contains("Recursive=true"))
        assertTrue(seriesAdded.contains("ParentId=237"))
        assertTrue(seriesAdded.contains("SortBy=DateLastContentAdded,DateCreated"))
        assertFalse(seriesAdded.contains("/Items/Latest"))
        val episodes = listOf(
            BaseItemDto(
                id = "e5",
                name = "第 5 集",
                type = "Episode",
                seriesId = "series-1",
                seriesName = "塔尔萨之王",
                seriesPrimaryImageTag = "series-primary",
                productionYear = 2026,
                imageTags = mapOf("Primary" to "episode-still"),
            ),
            BaseItemDto(
                id = "e4",
                name = "第 4 集",
                type = "Episode",
                seriesId = "series-1",
                seriesName = "塔尔萨之王",
                seriesPrimaryImageTag = "series-primary",
            ),
            BaseItemDto(id = "series-2", name = "巴比伦柏林", type = "Series", productionYear = 2017, imageTags = mapOf("Primary" to "bb")),
        )
        val cards = EmbyPresentation.coalesceSeriesCards(episodes)
        assertEquals(listOf("series-1", "series-2"), cards.map { it.id })
        assertEquals("塔尔萨之王", cards[0].name)
        assertEquals("Series", cards[0].type)
        assertEquals("series-primary", cards[0].imageTags?.get("Primary"))
        val seasonPath = seasonEpisodesPath("77420", "season-2", "user", "token")
        assertTrue(seasonPath.startsWith("/Shows/77420/Episodes?"))
        assertTrue(seasonPath.contains("SeasonId=season-2"))
        assertTrue(seasonPath.contains("UserId=user"))
        assertTrue(seasonPath.contains("Overview"))
        assertTrue(seasonPath.contains("PrimaryImageAspectRatio"))
        val still = BaseItemDto(
            id = "ep7",
            type = "Episode",
            parentBackdropItemId = "series-backdrop",
            parentBackdropImageTags = listOf("backdrop-tag"),
            imageTags = mapOf("Primary" to "still-tag", "Thumb" to "thumb-tag"),
        )
        val stillUrl = EmbyPresentation.episodeStillUrl("https://example.test", still, apiKey = "secret")
        assertTrue(stillUrl.contains("/Items/ep7/Images/Primary"))
        assertTrue(stillUrl.contains("tag=still-tag"))
        assertFalse(stillUrl.contains("series-backdrop"))
        assertTrue(stillUrl.contains("X-Emby-Token=secret"))
        val personUrl = EmbyPresentation.personImageUrl(
            "https://example.test",
            PersonInfo(id = "8298", name = "马特·达蒙", primaryImageTag = "face"),
            apiKey = "secret",
        )
        assertTrue(personUrl.contains("/Items/8298/Images/Primary"))
        assertTrue(personUrl.contains("tag=face"))
        assertTrue(personUrl.contains("api_key=secret"))
        assertEquals(
            "",
            EmbyPresentation.personImageUrl("https://example.test", PersonInfo(id = "1", name = "无名")),
        )
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
        assertTrue(thumb.contains("/Images/Backdrop"))
        assertTrue(thumb.contains("tag=${movie.backdropImageTags?.first()}"))
        assertEquals("", EmbyPresentation.landscapeImageUrl("https://example.test", BaseItemDto(id = "x", type = "Movie")))
        assertTrue(!EmbyPresentation.showContinueRow(null))
        assertTrue(!EmbyPresentation.showContinueRow(emptyList()))
        assertTrue(!EmbyPresentation.showContinueRow(listOf(BaseItemDto(name = "空白"))))
        assertTrue(EmbyPresentation.showContinueRow(listOf(movie)))
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
        val zeroRate = MediaSourceInfoDto(
            name = "2160p",
            bitrate = 192,
            mediaStreams = listOf(
                com.xxxx.emby_tv.data.model.MediaStreamDto(
                    type = "Video",
                    codec = "hevc",
                    displayTitle = "4K HDR 10 HEVC",
                    width = 3840,
                    height = 2160,
                    bitRate = 192,
                ),
            ),
        )
        val detail = EmbyPresentation.versionDetail(zeroRate)
        assertEquals("4K HDR 10 HEVC", detail)
        assertFalse(detail.contains("kbps"))
        assertEquals("", EmbyPresentation.versionDetail(MediaSourceInfoDto(name = "奥德赛", bitrate = 0)))
        val real = MediaSourceInfoDto(name = "2160p", bitrate = 8_000_000)
        assertTrue(EmbyPresentation.versionDetail(real).contains("8000 kbps"))
    }

    @Test
    fun mediaFactsUseResolutionAndRealAudioWithoutBitrate() {
        val clip = BaseItemDto(
            name = "山姆免费试播片段",
            type = "Movie",
            runTimeTicks = 2L * 60L * 10_000_000L,
            mediaSources = listOf(
                MediaSourceInfoDto(
                    name = "4K",
                    bitrate = 192,
                    mediaStreams = listOf(
                        com.xxxx.emby_tv.data.model.MediaStreamDto(
                            type = "Video",
                            width = 3840,
                            height = 1600,
                        ),
                        com.xxxx.emby_tv.data.model.MediaStreamDto(
                            type = "Audio",
                            codec = "eac3",
                            profile = "Dolby Digital Plus + Dolby Atmos",
                            channels = 6,
                            bitRate = 768_000,
                        ),
                    ),
                ),
            ),
        )
        assertEquals("2min", EmbyPresentation.runtimeLabel(clip.runTimeTicks))
        assertEquals("4K", EmbyPresentation.resolutionChip(clip))
        assertEquals("DD+ 5.1", EmbyPresentation.audioChip(clip))
        assertFalse(EmbyPresentation.audioChip(clip)!!.contains("kbps"))
    }
}
