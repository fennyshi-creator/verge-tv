package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.data.model.BaseItemDto

/**
 * Episodes shown while a series is playing: the current season only.
 * A season id means the fetch was already limited to that season.
 * Without one, [parentIndexNumber] picks the season out of the series list.
 */
object SeasonEpisodeList {
    fun forPlayback(
        seasonId: String?,
        parentIndexNumber: Int?,
        fetched: List<BaseItemDto>,
    ): List<BaseItemDto> {
        val scoped = if (!seasonId.isNullOrBlank()) {
            fetched
        } else if (parentIndexNumber != null) {
            fetched.filter { it.parentIndexNumber == parentIndexNumber }
        } else {
            fetched
        }
        return scoped.sortedWith(
            compareBy<BaseItemDto> { it.indexNumber ?: Int.MAX_VALUE }
                .thenBy { it.id.orEmpty() },
        )
    }
}
