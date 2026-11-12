package com.xxxx.emby_tv.data.model

/**
 * One Emby recommendation category, as returned by /Movies/Recommendations.
 * "因为你看过" rows use [baselineItemName] (the title the user already watched).
 */
data class RecommendationDto(
    val baselineItemName: String? = null,
    val recommendationType: String? = null,
    val items: List<BaseItemDto>? = null,
)
