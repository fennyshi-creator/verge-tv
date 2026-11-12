package com.xxxx.emby_tv.ui.detail

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.PersonInfo
import com.xxxx.emby_tv.data.model.UserDataDto

object SeriesDetailSample {
    private const val TICKS = 10_000_000L

    private fun episode(
        id: String,
        number: Int,
        name: String,
        minutes: Int,
        overview: String,
        progress: Float = 0f,
        season: Int = 2,
    ) = BaseItemDto(
        id = id,
        name = name,
        type = "Episode",
        seriesName = "TULSA KING",
        parentIndexNumber = season,
        indexNumber = number,
        overview = overview,
        runTimeTicks = minutes * 60L * TICKS,
        playedPercentage = progress * 100f,
        userData = UserDataDto(playbackPositionTicks = (minutes * 60L * TICKS * progress).toLong()),
        height = 1080,
        width = 1920,
    )

    val resume = episode(
        id = "sample-ep-support",
        number = 7,
        name = "生存支援",
        minutes = 40,
        overview = "德怀特和他的团队试图找出最近袭击的幕后黑手，纽约黑帮内部的权力动态开始发生变化。",
        progress = 0.72f,
    )

    val episodes = listOf(
        episode("sample-ep-prev", 6, "旧账", 41, "一场没谈拢的交易把旧账重新翻了出来。"),
        resume,
        episode("sample-ep-officer", 8, "新官上任", 42, "贝维拉卡准备复仇。瑟瑞舍和明讨论了一项新的安排。"),
        episode("sample-ep-three", 9, "三位一体", 36, "玛格丽特被卷入德怀特的生意中。随着局势进一步恶化，芬纳里奥牧场的平静也不复存在。"),
        episode("sample-ep-rebuild", 10, "重建", 35, "德怀特和他的团队在帝国扩张的同时，开始着手将未完成的事一一解决。"),
    )

    val seasonThree = listOf(
        episode("sample-s3-1", 1, "暗流", 38, "一场没写进账本的交易把第三季的局重新掀开。", season = 3),
        episode("sample-s3-2", 2, "赌局", 41, "牧场上的新客人不是来喝酒的。", season = 3),
        episode("sample-s3-3", 3, "旧牧场", 36, "德怀特回到那座没人再提起的牧场。", season = 3),
        episode("sample-s3-4", 4, "清算", 44, "该结的账在这一集里开始结清。", season = 3),
    )

    val seasons = listOf(
        BaseItemDto(id = "season-1", name = "第 1 季", type = "Season", indexNumber = 1),
        BaseItemDto(id = "season-2", name = "第 2 季", type = "Season", indexNumber = 2),
        BaseItemDto(id = "season-3", name = "第 3 季", type = "Season", indexNumber = 3),
        BaseItemDto(id = "season-4", name = "第 4 季", type = "Season", indexNumber = 4),
    )

    val series = BaseItemDto(
        id = "sample-tulsa",
        name = "TULSA KING",
        type = "Series",
        overview = "德怀特和他的团队试图找出最近袭击的幕后黑手，纽约黑帮内部的权力动态开始发生变化。",
        genres = listOf("犯罪", "剧情"),
        officialRating = "TV-MA",
        productionYear = 2022,
        status = "Continuing",
        communityRating = 8.3,
        people = listOf(
            PersonInfo(name = "西尔维斯特·史泰龙", role = "Dwight \"The General\" Manfredi"),
            PersonInfo(name = "马丁·斯塔尔", role = "Lawrence \"Bodhi\" Geidt"),
            PersonInfo(name = "杰伊·威尔", role = "Tyson Mitchell"),
            PersonInfo(name = "马克斯·卡塞拉", role = "Armand Truisi"),
            PersonInfo(name = "文森特·皮亚扎", role = "Vince Antonacci"),
            PersonInfo(name = "安娜贝拉·西奥拉", role = "Joanne"),
            PersonInfo(name = "塔蒂亚娜", role = "Tina"),
        ),
    )

    val similar = listOf(
        BaseItemDto(id = "sample-mayor", name = "金斯敦市长", type = "Series", productionYear = 2021, status = "Continuing"),
        BaseItemDto(id = "sample-dexter", name = "嗜血法医：杀戮复生", type = "Series", productionYear = 2025, status = "Continuing"),
        BaseItemDto(id = "sample-mobland", name = "黑帮领地", type = "Series", productionYear = 2025, status = "Continuing"),
        BaseItemDto(id = "sample-1883", name = "1883", type = "Series", productionYear = 2021, endDate = "2022-02-27", status = "Ended"),
        BaseItemDto(id = "sample-green", name = "黄石", type = "Series", productionYear = 2018, status = "Continuing"),
    )
}
