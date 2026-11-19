package com.xxxx.emby_tv.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.RecommendationDto
import com.xxxx.emby_tv.ui.home.HomePreviewChrome
import com.xxxx.emby_tv.ui.home.HomeSampleData

private const val TV = "spec:width=960dp,height=540dp,dpi=320"

private fun movie(id: String, name: String, year: Int) =
    BaseItemDto(id = id, name = name, type = "Movie", productionYear = year)

private val grid = listOf(
    movie("sample-odyssey", "奥德赛", 2026),
    movie("sample-love", "可能的爱", 2026),
    movie("sample-bio", "生化危机：爆发夜", 2026),
    movie("sample-gun", "空枪", 2026),
    movie("sample-north", "人生密密缝", 2017),
    movie("sample-dune", "忘却之岛", 2026),
    movie("sample-mood", "理智与情感", 1995),
    movie("sample-assassin", "寂地", 2024),
    movie("sample-mummy2", "青之炎", 2025),
    movie("sample-hero-film", "燕尾蝶", 2024),
    movie("sample-green", "功夫女足", 2023),
    movie("sample-zsjl", "十万火急", 2025),
)

private val latest = listOf(
    movie("sample-odyssey", "奥德赛", 2026),
    movie("sample-love", "可能的爱", 2026),
    movie("sample-bio", "生化危机：爆发夜", 2026),
    movie("sample-gun", "空枪", 2026),
    movie("sample-dune", "沙丘", 2021),
)

private val because = listOf(
    movie("sample-hero-film", "英雄", 2002),
    movie("sample-mummy2", "木乃伊2：木乃伊归来", 2001),
    movie("sample-mummy3", "木乃伊3：龙帝之墓", 2008),
    movie("sample-green", "绿区", 2010),
    movie("sample-mood", "花样年华", 2000),
)

@Preview(name = "library-movies-grid", device = TV, locale = "zh")
@Composable
fun LibraryMoviesGridPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        LibraryBrowsePage(
            libraryName = "电影Movies",
            itemType = "Movie",
            tab = LibraryTab.Items,
            onTab = {},
            items = grid,
            itemsLoading = false,
            suggestions = null,
            genres = emptyList(),
            selectedGenre = null,
            genreItems = emptyList(),
            genreLoading = false,
            serverUrl = "",
            onOpenItem = {},
            onOpenGenre = {},
            onLoadMore = {},
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}

@Preview(name = "library-suggestions", device = TV, locale = "zh")
@Composable
fun LibrarySuggestionsPreview(fontFamily: FontFamily = FontFamily.Default) {
    HomePreviewChrome(fontFamily) {
        LibraryBrowsePage(
            libraryName = "电影Movies",
            itemType = "Movie",
            tab = LibraryTab.Suggestions,
            onTab = {},
            items = HomeSampleData.movies,
            itemsLoading = false,
            suggestions = listOf(
                RecommendationDto(recommendationType = "Latest", items = latest),
                RecommendationDto(
                    baselineItemName = "蜘蛛侠：纵横宇宙",
                    recommendationType = "SimilarToRecentlyPlayed",
                    items = because,
                ),
            ),
            genres = emptyList(),
            selectedGenre = null,
            genreItems = emptyList(),
            genreLoading = false,
            serverUrl = "",
            onOpenItem = {},
            onOpenGenre = {},
            onLoadMore = {},
            requestInitialFocus = false,
            fontFamily = fontFamily,
        )
    }
}
