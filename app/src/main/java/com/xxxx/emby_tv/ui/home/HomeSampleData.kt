package com.xxxx.emby_tv.ui.home

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.RecommendationDto
import com.xxxx.emby_tv.data.model.UserDataDto

object HomeSampleData {
    val hero = item("sample-featured", "夜色", 2026, "Movie")

    val resume = listOf(
        resume("sample-odyssey", "奥德赛", 2026, 0.34f),
        resume("sample-malena", "西西里的美丽传说", 2000, 0.07f),
        resume("sample-lincoln", "林肯律师", 2011, 0.58f),
        episode(),
        resume("sample-dune", "沙丘", 2021, 0.22f),
        resume("sample-love", "可能的爱", 2026, 0.41f),
        resume("sample-bio", "生化危机：爆发夜", 2026, 0.18f),
        resume("sample-gun", "空枪", 2026, 0.66f),
        resume("sample-hero-film", "英雄", 2002, 0.27f),
        resume("sample-mood", "花样年华", 2000, 0.83f),
    )

    val movies = listOf(
        item("sample-odyssey", "奥德赛", 2026, "Movie"),
        item("sample-love", "可能的爱", 2026, "Movie"),
        item("sample-bio", "生化危机：爆发夜", 2026, "Movie"),
        item("sample-gun", "空枪", 2026, "Movie"),
        item("sample-dune", "沙丘", 2021, "Movie"),
    )

    val series = listOf(
        item("sample-affair", "现在不是出轨的问题", 2024, "Series"),
        item("sample-season", "漫长的季节", 2023, "Series"),
        item("sample-wind", "去有风的地方", 2023, "Series"),
        item("sample-blossom", "繁花", 2023, "Series"),
        item("sample-joy", "庆余年", 2019, "Series"),
    )

    val collections = listOf(
        item("sample-set-a", "华语经典", 2020, "BoxSet"),
        item("sample-set-b", "科幻特辑", 2022, "BoxSet"),
        item("sample-set-c", "导演影单", 2018, "BoxSet"),
        item("sample-set-d", "武侠世界", 2002, "BoxSet"),
    )

    val libraries = listOf(
        item("sample-lib-movies", "电影Movies", null, "CollectionFolder", movies),
        item("sample-lib-series", "剧集Series", null, "CollectionFolder", series),
        item("sample-lib-sets", "专题影单", null, "CollectionFolder", collections),
    )

    val becauseGrandmaster = RecommendationDto(
        baselineItemName = "一代宗师",
        recommendationType = "SimilarToRecentlyPlayed",
        items = listOf(
            item("sample-hero-film", "英雄", 2002, "Movie"),
            item("sample-assassin", "刺客聂隐娘", 2015, "Movie"),
            item("sample-mood", "花样年华", 2000, "Movie"),
            item("sample-kekexili", "可可西里", 2004, "Movie"),
            item("sample-rurouni", "浪客剑心", 2012, "Movie"),
        ),
    )

    val becauseOdyssey = RecommendationDto(
        baselineItemName = "奥德赛",
        recommendationType = "SimilarToRecentlyPlayed",
        items = listOf(
            item("sample-mummy2", "木乃伊2：木乃伊归来", 2001, "Movie"),
            item("sample-mummy3", "木乃伊3：龙帝之墓", 2008, "Movie"),
            item("sample-green", "绿区", 2010, "Movie"),
            item("sample-rurouni-end", "浪客剑心：传说的完结篇", 2014, "Movie"),
            item("sample-dune", "沙丘", 2021, "Movie"),
        ),
    )

    private fun item(
        id: String,
        name: String,
        year: Int?,
        type: String,
        latest: List<BaseItemDto>? = null,
    ) = BaseItemDto(
        id = id,
        name = name,
        type = type,
        productionYear = year,
        latestItems = latest,
        primaryImageAspectRatio = if (type == "Episode") 1.77 else 0.67,
    )

    private fun resume(id: String, name: String, year: Int, progress: Float) =
        item(id, name, year, "Movie").copy(playedPercentage = progress * 100f, userData = UserDataDto())

    private fun episode() = BaseItemDto(
        id = "sample-affair-ep",
        name = "不速之客",
        seriesName = "现在不是出轨的问题",
        seriesId = "sample-affair",
        type = "Episode",
        parentIndexNumber = 1,
        indexNumber = 7,
        playedPercentage = 74f,
        userData = UserDataDto(),
        primaryImageAspectRatio = 1.77,
    )
}
