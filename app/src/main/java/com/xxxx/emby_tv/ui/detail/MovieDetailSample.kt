package com.xxxx.emby_tv.ui.detail

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.PersonInfo
import com.xxxx.emby_tv.data.model.UserDataDto

object MovieDetailSample {
    private const val TICKS = 10_000_000L

    val movie = BaseItemDto(
        id = "sample-odyssey",
        name = "奥德赛",
        type = "Movie",
        overview = "特洛伊战争结束后，奥德修斯在返回伊萨卡的途中历经艰险，沿途遇到了独眼巨人波吕斐摩斯、塞壬女妖和喀耳刻等神奇生物。",
        genres = listOf("冒险", "动作"),
        officialRating = "R",
        productionYear = 2026,
        communityRating = 8.0,
        runTimeTicks = (2 * 3600 + 52 * 60) * TICKS,
        width = 3840,
        height = 2160,
        userData = UserDataDto(playbackPositionTicks = 10 * 60 * TICKS),
        people = listOf(
            PersonInfo(name = "马特·达蒙", id = "p1", role = "Odysseus"),
            PersonInfo(name = "汤姆·赫兰德", id = "p2", role = "Telemachus"),
            PersonInfo(name = "安妮·海瑟薇", id = "p3", role = "Penelope"),
            PersonInfo(name = "罗伯特·帕丁森", id = "p4", role = "Antinous"),
            PersonInfo(name = "希米什·帕特尔", id = "p5", role = "Eurylochus"),
            PersonInfo(name = "查理兹·塞隆", id = "p6", role = "Calypso"),
            PersonInfo(name = "约翰·特托罗", id = "p7", role = "Poseidon"),
        ),
        mediaSources = listOf(
            MediaSourceInfoDto(
                id = "src-4k",
                name = "2160p",
                bitrate = 192,
                mediaStreams = listOf(
                    MediaStreamDto(
                        width = 3840,
                        height = 2160,
                        type = "Video",
                        codec = "hevc",
                        displayTitle = "4K HDR 10 HEVC",
                        bitRate = 192,
                    ),
                ),
            ),
            MediaSourceInfoDto(
                id = "src-1080",
                name = "1080p",
                mediaStreams = listOf(
                    MediaStreamDto(
                        width = 1920,
                        height = 1080,
                        type = "Video",
                        codec = "hevc",
                        displayTitle = "1080p HEVC",
                    ),
                ),
            ),
            MediaSourceInfoDto(
                id = "src-other",
                name = "奥德赛",
            ),
        ),
    )

    val similar = listOf(
        BaseItemDto(id = "sample-mummy2", name = "木乃伊2：木乃伊归来", type = "Movie", productionYear = 2001),
        BaseItemDto(id = "sample-mummy3", name = "木乃伊3：龙帝之墓", type = "Movie", productionYear = 2008),
        BaseItemDto(id = "sample-north", name = "北欧人", type = "Movie", productionYear = 2022),
        BaseItemDto(id = "sample-zsjl", name = "扎克·施奈德版正义联盟", type = "Movie", productionYear = 2021),
        BaseItemDto(id = "sample-green", name = "绿区", type = "Movie", productionYear = 2010),
    )
}
