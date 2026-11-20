package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.data.model.MediaSourceInfoDto

/**
 * The playback bar's version list. PlaybackInfo often returns only the
 * source that is about to play. The item catalog is the same MediaSources
 * list the details page uses, and a shorter reply must not erase it.
 */
object VersionCatalog {
    fun choose(
        playback: List<MediaSourceInfoDto>,
        item: List<MediaSourceInfoDto>,
        cached: List<MediaSourceInfoDto>,
    ): List<MediaSourceInfoDto> {
        return sequenceOf(playback, item, cached)
            .filter { it.isNotEmpty() }
            .maxByOrNull { it.size }
            ?: emptyList()
    }
}
