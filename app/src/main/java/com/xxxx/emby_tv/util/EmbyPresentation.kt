package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.PersonInfo
import java.util.Locale

/**
 * Display mapping from Emby items to the approved home and detail screens.
 * Pure functions so fixture tests can lock the live-library contract.
 */
object EmbyPresentation {
    const val OTHER_VERSION = "其他版本"
    private const val TICKS_PER_MINUTE = 600_000_000L

    fun displayName(item: BaseItemDto): String = item.name.orEmpty()

    fun hasLogo(item: BaseItemDto): Boolean = !item.imageTags?.get("Logo").isNullOrBlank()

    fun logoUrl(serverUrl: String, item: BaseItemDto, maxWidth: Int = 800): String {
        val tag = item.imageTags?.get("Logo") ?: return ""
        val id = item.id ?: return ""
        if (serverUrl.isBlank() || tag.isBlank()) return ""
        return "$serverUrl/emby/Items/$id/Images/Logo?maxWidth=$maxWidth&tag=$tag&quality=90"
    }

    fun resumeImageUrl(serverUrl: String, item: BaseItemDto): String {
        if (serverUrl.isBlank()) return ""
        val id = item.id
        val tags = item.imageTags
        val thumb = tags?.get("Thumb")
        if (!id.isNullOrBlank() && !thumb.isNullOrBlank()) {
            return "$serverUrl/emby/Items/$id/Images/Thumb?maxWidth=800&tag=$thumb&quality=80"
        }
        if (item.type == "Episode") {
            val primary = tags?.get("Primary")
            if (!id.isNullOrBlank() && !primary.isNullOrBlank()) {
                return "$serverUrl/emby/Items/$id/Images/Primary?maxWidth=800&tag=$primary&quality=80"
            }
        }
        return com.xxxx.emby_tv.Utils.getImageUrl(serverUrl, item, true)
    }

    fun personImageUrl(
        serverUrl: String,
        person: PersonInfo,
        apiKey: String? = null,
        maxWidth: Int = 400,
    ): String {
        val id = person.id ?: return ""
        val tag = person.primaryImageTag ?: return ""
        if (serverUrl.isBlank() || tag.isBlank()) return ""
        return "$serverUrl/emby/Items/$id/Images/Primary?maxWidth=$maxWidth&tag=$tag&quality=80${tokenQuery(apiKey)}"
    }

    /**
     * One series card. Episodes and seasons that leak out of a latest query collapse
     * onto SeriesId, and the poster tag is the series primary, not the episode still.
     */
    fun coalesceSeriesCards(items: List<BaseItemDto>): List<BaseItemDto> {
        val seen = LinkedHashSet<String>()
        val cards = ArrayList<BaseItemDto>()
        for (item in items) {
            val card = toSeriesCard(item) ?: continue
            val id = card.id ?: continue
            if (!seen.add(id)) continue
            cards += card
        }
        return cards
    }

    fun toSeriesCard(item: BaseItemDto): BaseItemDto? {
        if (item.type.equals("Series", true)) {
            return item.takeIf { !it.id.isNullOrBlank() }
        }
        if (item.type != "Episode" && item.type != "Season") return null
        val seriesId = item.seriesId?.takeIf { it.isNotBlank() } ?: return null
        val name = item.seriesName?.takeIf { it.isNotBlank() } ?: item.name
        val tag = item.seriesPrimaryImageTag?.takeIf { it.isNotBlank() }
        return BaseItemDto(
            id = seriesId,
            name = name,
            type = "Series",
            productionYear = item.productionYear,
            seriesId = seriesId,
            seriesName = name,
            seriesPrimaryImageTag = tag,
            imageTags = if (tag == null) null else mapOf("Primary" to tag),
        )
    }

    /**
     * Episode still: own primary, own thumb, season primary, then series backdrop.
     * Parent backdrop is not used first — that painted the series art on every card.
     */
    fun episodeStillUrl(
        serverUrl: String,
        episode: BaseItemDto,
        season: BaseItemDto? = null,
        series: BaseItemDto? = null,
        apiKey: String? = null,
        maxWidth: Int = 800,
    ): String {
        if (serverUrl.isBlank()) return ""
        val token = tokenQuery(apiKey)
        fun image(id: String?, kind: String, tag: String?): String? {
            if (id.isNullOrBlank() || tag.isNullOrBlank()) return null
            return "$serverUrl/emby/Items/$id/Images/$kind?maxWidth=$maxWidth&tag=$tag&quality=80$token"
        }
        image(episode.id, "Primary", episode.imageTags?.get("Primary"))?.let { return it }
        image(episode.id, "Thumb", episode.imageTags?.get("Thumb"))?.let { return it }
        image(season?.id, "Primary", season?.imageTags?.get("Primary"))?.let { return it }
        val backdropTag = series?.backdropImageTags?.firstOrNull()
        val backdropId = series?.id
        if (!backdropId.isNullOrBlank() && !backdropTag.isNullOrBlank()) {
            return "$serverUrl/emby/Items/$backdropId/Images/Backdrop?maxWidth=$maxWidth&tag=$backdropTag&quality=80$token"
        }
        return ""
    }

    private fun tokenQuery(apiKey: String?): String {
        if (apiKey.isNullOrBlank()) return ""
        val encoded = java.net.URLEncoder.encode(apiKey, "UTF-8")
        return "&api_key=$encoded&X-Emby-Token=$encoded"
    }

    fun genresLine(item: BaseItemDto): String = item.genres.orEmpty().filter { it.isNotBlank() }.joinToString(" / ")

    fun starLabel(rating: Double?): String? {
        if (rating == null) return null
        return "★" + String.format(Locale.US, "%.1f", rating)
    }

    fun yearLabel(item: BaseItemDto): String {
        val start = item.productionYear ?: return ""
        if (!item.type.equals("Series", true)) return start.toString()
        val endDate = item.endDate?.take(4)?.takeIf { it.length == 4 && it.all { ch -> ch.isDigit() } }
        val ended = item.status.equals("Ended", true)
        return when {
            ended && endDate != null && endDate != start.toString() -> "$start - $endDate"
            endDate == null || !ended -> "$start - 现在"
            else -> start.toString()
        }
    }

    fun runtimeMinutes(ticks: Long?): Long? {
        if (ticks == null || ticks <= 0L) return null
        return ticks / TICKS_PER_MINUTE
    }

    fun runtimeLabel(ticks: Long?): String? {
        val minutes = runtimeMinutes(ticks) ?: return null
        val hours = minutes / 60
        val remain = minutes % 60
        return if (hours > 0) "${hours}h ${remain}min" else "${minutes}min"
    }

    /** Full action label: 「播放」 or 「续看 · 还剩 …」. Played does not hide a real resume point. */
    fun playActionLabel(item: BaseItemDto): String {
        val remaining = remainingClock(item) ?: return "播放"
        return "续看 · 还剩 $remaining"
    }

    fun remainingClock(item: BaseItemDto): String? {
        val runtime = item.runTimeTicks ?: return null
        val position = item.userData?.playbackPositionTicks ?: 0L
        if (position <= 0L || runtime <= position) return null
        val minutes = (runtime - position) / TICKS_PER_MINUTE
        if (minutes < 1L) return null
        val hours = minutes / 60
        val remain = minutes % 60
        return if (hours > 0) "${hours}h ${remain}min" else "${minutes}min"
    }

    fun episodeHeading(item: BaseItemDto): String {
        val season = item.parentIndexNumber
        val episode = item.indexNumber
        val name = item.name.orEmpty()
        return if (season != null && episode != null) "S$season:E$episode - $name" else name
    }

    fun episodeMeta(item: BaseItemDto): String {
        val number = item.indexNumber ?: return ""
        val runtime = runtimeMinutes(item.runTimeTicks)?.let { "${it}m" }.orEmpty()
        return if (runtime.isBlank()) "Episode $number" else "Episode $number · $runtime"
    }

    fun resumeCardTitle(item: BaseItemDto): String {
        return if (item.type == "Episode") {
            item.seriesName?.takeIf { it.isNotBlank() } ?: item.name.orEmpty()
        } else {
            item.name.orEmpty()
        }
    }

    fun resumeCardSubtitle(item: BaseItemDto): String {
        if (item.type == "Episode" && item.parentIndexNumber != null && item.indexNumber != null) {
            val episodeName = item.name?.takeIf { it.isNotBlank() }.orEmpty()
            val code = "S${item.parentIndexNumber}:E${item.indexNumber}"
            return if (episodeName.isBlank()) code else "$code - $episodeName"
        }
        return item.productionYear?.toString().orEmpty()
    }

    fun seasonTabLabel(season: BaseItemDto): String = season.name.orEmpty()

    fun qualityBadge(item: BaseItemDto): String? {
        val ranked = item.mediaSources.orEmpty().mapNotNull { identifiableQuality(it) }
        return bestQuality(ranked)
    }

    fun versionLabel(source: MediaSourceInfoDto): String = identifiableQuality(source) ?: OTHER_VERSION

    /**
     * Secondary line under a version. Bitrate is omitted when it is missing or too small
     * to be a real rate (a strm probe of 192 bps otherwise prints as "0 kbps").
     */
    fun versionDetail(source: MediaSourceInfoDto): String {
        val video = source.mediaStreams.orEmpty().firstOrNull { it.type.equals("Video", true) }
        val head = video?.displayTitle?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                identifiableQuality(source),
                video?.codec?.takeIf { it.isNotBlank() }?.uppercase(Locale.US),
            ).joinToString(" ")
        val bitrate = source.bitrate ?: video?.bitRate
        val rate = if (bitrate != null && bitrate >= 1000) {
            val kbps = bitrate / 1000
            if (kbps > 0) "$kbps kbps" else ""
        } else {
            ""
        }
        return listOf(head, rate).filter { it.isNotBlank() }.joinToString(" / ")
    }

    /** Movies, series, and boxsets each use a typed Latest query. Null means "do not list". */
    fun latestIncludeTypes(view: BaseItemDto): String? = when (view.collectionType?.lowercase(Locale.US)) {
        "movies" -> "Movie"
        "tvshows" -> "Series"
        "boxsets" -> "BoxSet"
        else -> null
    }

    /**
     * Series the suggestions tab should ask Similar for: resume episodes first,
     * then series sorted by DatePlayed. Duplicates are dropped.
     */
    fun recentSeriesSeeds(
        resume: List<BaseItemDto>,
        played: List<BaseItemDto>,
        limit: Int = 4,
    ): List<Pair<String, String>> {
        val seeds = linkedMapOf<String, String>()
        fun add(id: String?, name: String?) {
            if (seeds.size >= limit) return
            if (id.isNullOrBlank() || name.isNullOrBlank() || seeds.containsKey(id)) return
            seeds[id] = name
        }
        for (item in resume) {
            if (item.type == "Series") add(item.id, item.name)
            else add(item.seriesId, item.seriesName ?: item.name)
        }
        for (item in played) add(item.id, item.name)
        return seeds.map { it.key to it.value }
    }

    fun isPosterRowLibrary(view: BaseItemDto): Boolean {
        if (latestIncludeTypes(view) == "Movie" || latestIncludeTypes(view) == "Series") return true
        val child = view.latestItems?.firstOrNull()?.type
        return child == "Movie" || child == "Series"
    }

    /**
     * View tiles (especially 专题影单) carry art on ImageTags.Primary. A separate boolean
     * primary flag is not part of this payload and must not gate the URL.
     */
    fun viewPrimaryImageUrl(serverUrl: String, view: BaseItemDto, maxWidth: Int = 800): String {
        if (serverUrl.isBlank()) return ""
        val tag = view.imageTags?.get("Primary")?.takeIf { it.isNotBlank() } ?: return ""
        val id = view.id?.takeIf { it.isNotBlank() } ?: return ""
        return "$serverUrl/emby/Items/$id/Images/Primary?maxWidth=$maxWidth&tag=$tag&quality=80"
    }

    fun identifiableQuality(source: MediaSourceInfoDto): String? {
        val video = source.mediaStreams.orEmpty().firstOrNull { it.type.equals("Video", true) }
        val fromPixels = video?.let { qualityFromPixels(it.width, it.height) }
        if (fromPixels != null) return fromPixels
        return qualityFromName(source.name)
    }

    private fun qualityFromPixels(width: Int?, height: Int?): String? {
        val h = height ?: 0
        val w = width ?: 0
        return when {
            h >= 2160 || w >= 3840 -> "4K"
            h >= 1080 || w >= 1920 -> "1080p"
            h >= 720 || w >= 1280 -> "720p"
            else -> null
        }
    }

    private fun qualityFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val normalized = name.lowercase(Locale.US)
        return when {
            normalized.contains("2160") || normalized.contains("4k") -> "4K"
            normalized.contains("1080") -> "1080p"
            normalized.contains("720") -> "720p"
            else -> null
        }
    }

    private fun bestQuality(labels: List<String>): String? {
        return listOf("4K", "1080p", "720p").firstOrNull { it in labels }
    }
}
