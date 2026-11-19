package com.xxxx.emby_tv.util

import com.xxxx.emby_tv.pickPreferredMediaSource
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.PersonInfo
import java.util.Locale

enum class VersionControl { Hidden, Icon, Label, Menu }

/** Library grid cell for a flat item index. Back uses this to return to the same poster. */
object LibraryFocus {
    fun cell(index: Int, columns: Int = 6): Pair<Int, Int> {
        if (index < 0 || columns <= 0) return 0 to 0
        return index / columns to index % columns
    }
}

/**
 * Display mapping from Emby items to the approved home and detail screens.
 * Pure functions so fixture tests can lock the live-library contract.
 */
object EmbyPresentation {
    const val OTHER_VERSION = "其他版本"
    private const val TICKS_PER_MINUTE = 600_000_000L

    fun displayName(item: BaseItemDto): String = item.name.orEmpty()

    /** Homepage hero line: genres · year · ★ rating. Missing pieces are dropped. */
    fun heroMetaLine(genres: List<String>?, year: Int?, rating: Double?): String {
        val parts = ArrayList<String>()
        val names = genres.orEmpty().filter { it.isNotBlank() }.take(2)
        if (names.isNotEmpty()) parts += names.joinToString(" · ")
        if (year != null && year > 0) parts += year.toString()
        if (rating != null && rating > 0.0) {
            val tenths = kotlin.math.round(rating * 10.0).toInt()
            parts += "★ ${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
        }
        return parts.joinToString(" · ")
    }

    fun hasLogo(item: BaseItemDto): Boolean = !item.imageTags?.get("Logo").isNullOrBlank()

    fun logoUrl(serverUrl: String, item: BaseItemDto, maxWidth: Int = 800): String {
        val tag = item.imageTags?.get("Logo") ?: return ""
        val id = item.id ?: return ""
        if (serverUrl.isBlank() || tag.isBlank()) return ""
        return "$serverUrl/emby/Items/$id/Images/Logo?maxWidth=$maxWidth&tag=$tag&quality=90"
    }

    fun resumeImageUrl(serverUrl: String, item: BaseItemDto): String {
        if (item.type.equals("Episode", true)) {
            return episodeStillUrl(serverUrl, item, maxWidth = 800)
        }
        return landscapeImageUrl(serverUrl, item, maxWidth = 800)
    }

    /**
     * Wide card art. Backdrop, then thumb, then a parent backdrop or thumb.
     * The portrait primary is only used when none of those tags exist, so a
     * poster is not stretched across a landscape slot. A missing tag yields
     * no URL rather than a request for an empty tag.
     */
    fun landscapeImageUrl(serverUrl: String, item: BaseItemDto, maxWidth: Int = 800): String {
        if (serverUrl.isBlank()) return ""
        fun image(id: String?, kind: String, tag: String?): String? {
            if (id.isNullOrBlank() || tag.isNullOrBlank()) return null
            return "$serverUrl/emby/Items/$id/Images/$kind?maxWidth=$maxWidth&tag=$tag&quality=80"
        }
        image(item.id, "Backdrop", item.backdropImageTags?.firstOrNull())?.let { return it }
        image(item.id, "Thumb", item.imageTags?.get("Thumb"))?.let { return it }
        image(item.parentBackdropItemId, "Backdrop", item.parentBackdropImageTags?.firstOrNull())?.let { return it }
        image(item.parentThumbItemId, "Thumb", item.parentThumbImageTag)?.let { return it }
        image(item.id, "Primary", item.imageTags?.get("Primary"))?.let { return it }
        return ""
    }

    /** A continue row exists only when the server returned at least one real item. */
    fun showContinueRow(items: List<BaseItemDto>?): Boolean =
        items.orEmpty().any { !it.id.isNullOrBlank() }

    fun hasOwnBackdrop(item: BaseItemDto): Boolean = !item.backdropImageTags.isNullOrEmpty()

    /** Up to three newest movies and three newest series that have their own backdrop. */
    fun heroPool(libraries: List<BaseItemDto>): List<BaseItemDto> {
        fun take(kind: String): List<BaseItemDto> {
            val rows = libraries.filter { latestIncludeTypes(it) == kind }
            return rows.flatMap { it.latestItems.orEmpty() }
                .filter { it.type == kind && !it.id.isNullOrBlank() && hasOwnBackdrop(it) }
                .distinctBy { it.id }
                .take(3)
        }
        return take("Movie") + take("Series")
    }

    fun pickHero(pool: List<BaseItemDto>, index: Int): BaseItemDto? {
        if (pool.isEmpty()) return null
        val at = index.mod(pool.size)
        return pool[if (at < 0) at + pool.size else at]
    }

    /** Episode rating, then series, then season. Blank means show nothing. */
    fun playbackRating(episode: String?, series: String?, season: String?): String? =
        listOf(episode, series, season).firstOrNull { !it.isNullOrBlank() }?.trim()

    /** On-screen line. Blank ratings stay hidden. */
    fun ratingLine(rating: String?): String? {
        val text = rating?.trim().orEmpty()
        if (text.isEmpty()) return null
        return "分级为 $text"
    }

    /** Fade in, hold, fade out. After that the rating stays hidden for the session. */
    fun ratingAlpha(elapsedMs: Long, fadeMs: Long = 400L, holdMs: Long = 3500L): Float {
        if (elapsedMs < 0L) return 0f
        if (elapsedMs < fadeMs) return elapsedMs.toFloat() / fadeMs
        if (elapsedMs < fadeMs + holdMs) return 1f
        val out = elapsedMs - fadeMs - holdMs
        if (out >= fadeMs) return 0f
        return 1f - out.toFloat() / fadeMs
    }

    const val FAVORITES_TITLE = "收藏影片/剧集"

    /**
     * Movies and series the user favorited, from every library. A favorited
     * episode is shown as its parent series, once.
     */
    fun favoritesForDisplay(items: List<BaseItemDto>): List<BaseItemDto> {
        val order = LinkedHashMap<String, BaseItemDto>()
        for (item in items) {
            if (item.type != "Movie" && item.type != "Series") continue
            val id = item.id ?: continue
            order.putIfAbsent(id, item)
        }
        for (item in items) {
            if (!item.type.equals("Episode", true)) continue
            val id = item.seriesId ?: continue
            if (order.containsKey(id)) continue
            order[id] = BaseItemDto(
                id = id,
                name = item.seriesName ?: item.name,
                type = "Series",
                productionYear = item.productionYear,
            )
        }
        return order.values.toList()
    }

    /** Homepage library tile: movies and series keep the browse grid; box sets get the collection list. */
    fun libraryDestination(view: BaseItemDto): String = when (latestIncludeTypes(view)) {
        "Movie", "Series" -> "browse"
        "BoxSet" -> "collections"
        else -> "library"
    }

    fun detailRoute(item: BaseItemDto): String? {
        val id = item.id ?: return null
        return when {
            item.type.equals("Series", true) || item.isSeries -> "series/$id"
            item.type.equals("Movie", true) -> "media/$id"
            item.type.equals("BoxSet", true) -> "collection/$id"
            item.type.equals("Person", true) -> "person/$id"
            else -> null
        }
    }

    fun collectionMetaLine(genres: List<String>?, rating: String?): String {
        val names = genres.orEmpty().filter { it.isNotBlank() }.take(3)
        val head = names.joinToString(" / ")
        val mark = rating?.takeIf { it.isNotBlank() }
        return listOf(head, mark).filter { !it.isNullOrBlank() }.joinToString(" · ")
    }

    /** Movies and series inside a box. An episode is shown as its parent series, once. */
    fun collectionChildrenForDisplay(items: List<BaseItemDto>): List<BaseItemDto> =
        favoritesForDisplay(items)

    /**
     * A person's works. Episodes collapse to the series name so a series row
     * is never an episode title.
     */
    fun personWorks(items: List<BaseItemDto>): Pair<List<BaseItemDto>, List<BaseItemDto>> {
        val movies = LinkedHashMap<String, BaseItemDto>()
        for (item in items) {
            if (!item.type.equals("Movie", true)) continue
            val id = item.id ?: continue
            movies.putIfAbsent(id, item)
        }
        val series = coalesceSeriesCards(
            items.filter {
                it.type.equals("Series", true) ||
                    it.type.equals("Episode", true) ||
                    it.type.equals("Season", true)
            },
        )
        return movies.values.toList() to series
    }

    fun personFactLine(
        premiere: String?,
        end: String?,
        places: List<String>?,
        todayYear: Int,
        todayMonth: Int,
        todayDay: Int,
    ): String {
        val birth = isoDate(premiere)
        val place = places.orEmpty().firstOrNull { it.isNotBlank() }
        if (birth == null) return place.orEmpty()
        val (year, month, day) = birth
        val endDate = isoDate(end)
        val ageYear = endDate?.first ?: todayYear
        val ageMonth = endDate?.second ?: todayMonth
        val ageDay = endDate?.third ?: todayDay
        var age = ageYear - year
        if (ageMonth < month || (ageMonth == month && ageDay < day)) age--
        val parts = ArrayList<String>()
        parts += "${year}年${month}月${day}日"
        if (age in 0..130) parts += "${age}岁"
        if (!place.isNullOrBlank()) parts += place
        return parts.joinToString(" · ")
    }

    private fun isoDate(value: String?): Triple<Int, Int, Int>? {
        if (value.isNullOrBlank() || value.length < 10) return null
        val year = value.substring(0, 4).toIntOrNull() ?: return null
        val month = value.substring(5, 7).toIntOrNull() ?: return null
        val day = value.substring(8, 10).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return Triple(year, month, day)
    }

    fun sortFavorites(items: List<BaseItemDto>, byYear: Boolean): List<BaseItemDto> {
        return if (byYear) {
            items.sortedWith(compareByDescending<BaseItemDto> { it.productionYear ?: 0 }.thenBy { it.name.orEmpty() })
        } else {
            items.sortedBy { it.name.orEmpty() }
        }
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
        return bestQuality(ranked) ?: qualityFromPixels(item.width, item.height)
    }

    /** White badge text. 4K stays 4K. 1080p is shown as 1080P. */
    fun resolutionChip(item: BaseItemDto): String? = resolutionText(qualityBadge(item))

    fun resolutionChip(source: MediaSourceInfoDto): String? = resolutionText(identifiableQuality(source))

    fun resolutionText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return when (raw.lowercase(Locale.US)) {
            "4k", "2160p" -> "4K"
            "1080p" -> "1080P"
            "720p" -> "720P"
            else -> raw
        }
    }

    /**
     * Audio chip from the real stream. "DD+ 5.1" for E-AC-3. Bitrate is never included.
     */
    fun audioChip(item: BaseItemDto): String? {
        val streams = item.mediaStreams?.takeIf { it.isNotEmpty() }
            ?: item.mediaSources?.firstOrNull()?.mediaStreams
        return audioChip(streams)
    }

    fun audioChip(source: MediaSourceInfoDto): String? = audioChip(source.mediaStreams)

    fun audioChip(streams: List<com.xxxx.emby_tv.data.model.MediaStreamDto>?): String? {
        val audio = streams.orEmpty()
            .filter { it.type.equals("Audio", true) }
            .maxByOrNull { it.channels ?: 0 }
            ?: return null
        val codec = audio.codec?.lowercase(Locale.US).orEmpty()
        val name = when (codec) {
            "eac3", "e-ac3", "eac-3" -> "DD+"
            "ac3", "ac-3" -> "DD"
            "truehd", "mlp" -> "TrueHD"
            "aac" -> "AAC"
            "dts", "dca" -> "DTS"
            "flac" -> "FLAC"
            "mp3" -> "MP3"
            "opus" -> "Opus"
            "" -> return null
            else -> codec.uppercase(Locale.US)
        }
        val layout = when (audio.channels) {
            8 -> "7.1"
            7 -> "6.1"
            6 -> "5.1"
            5 -> "5.0"
            4 -> "4.0"
            3 -> "2.1"
            2 -> "2.0"
            1 -> "1.0"
            else -> audio.channelLayout
                ?.takeIf { it.isNotBlank() && !it.contains("kbps", true) && !it.contains("0 kbps", true) }
        }
        return if (layout.isNullOrBlank()) name else "$name $layout"
    }

    fun versionLabel(source: MediaSourceInfoDto): String = identifiableQuality(source) ?: OTHER_VERSION

    /** Button text. Resolution only, in the 1080P / 2160P form. A probe bitrate of 0 is never shown. */
    fun versionButtonLabel(source: MediaSourceInfoDto): String {
        val quality = identifiableQuality(source).orEmpty()
            .substringBefore("kbps")
            .substringBefore("·")
            .trim()
        val shown = when (quality.lowercase(Locale.US)) {
            "4k", "2160p" -> "4K"
            "1080p" -> "1080P"
            "720p" -> "720P"
            else -> quality.uppercase(Locale.US)
        }
        return shown.ifBlank { OTHER_VERSION }
    }

    fun showVersionButton(sourceCount: Int): Boolean = sourceCount > 1

    /** Same source the chips describe. Preference order matches playback. */
    fun defaultSourceIndex(sources: List<MediaSourceInfoDto>, preference: Int): Int {
        if (sources.isEmpty()) return 0
        val picked = pickPreferredMediaSource(sources, preference) ?: return 0
        val byId = sources.indexOfFirst { !it.id.isNullOrBlank() && it.id == picked.id }
        if (byId >= 0) return byId
        val byRef = sources.indexOf(picked)
        return if (byRef >= 0) byRef else 0
    }

    /** Confirming a version closes or opens the menu. It never starts playback. */
    fun versionConfirmStartsPlayback(): Boolean = false

    fun versionMenuAfterConfirm(menuOpen: Boolean): Boolean = !menuOpen

    fun runtimeTicksFor(item: BaseItemDto, source: MediaSourceInfoDto?): Long? =
        source?.runTimeTicks?.takeIf { it > 0L } ?: item.runTimeTicks

    /** Saved resume point stays on the title when the selected version changes. */
    fun resumePositionTicks(item: BaseItemDto): Long =
        item.userData?.playbackPositionTicks ?: 0L

    /**
     * HDR chip for the selected source. SDR and unknown metadata stay blank.
     * Dolby Vision uses the settings wording 杜比视界.
     */
    fun hdrChip(item: BaseItemDto, source: MediaSourceInfoDto? = null): String? {
        val video = source?.mediaStreams?.firstOrNull { it.type.equals("Video", true) }
            ?: item.mediaStreams?.firstOrNull { it.type.equals("Video", true) }
            ?: item.mediaSources?.firstOrNull()?.mediaStreams?.firstOrNull { it.type.equals("Video", true) }
            ?: return null
        return hdrLabel(
            video.videoRangeType,
            video.extendedVideoType,
            video.videoRange,
            video.colorTransfer,
            video.displayTitle,
        )
    }

    fun hdrLabel(
        rangeType: String?,
        extendedType: String?,
        videoRange: String?,
        colorTransfer: String?,
        displayTitle: String? = null,
    ): String? {
        val typed = listOfNotNull(rangeType, extendedType).joinToString(" ").lowercase(Locale.US)
        val range = videoRange?.trim()?.lowercase(Locale.US).orEmpty()
        if (typed.contains("dovi") || typed.contains("dolby")) return "杜比视界"
        if (range == "sdr" || typed == "sdr") return null
        if (typed.contains("hlg") || range == "hlg") return "HLG"
        if (typed.contains("hdr10") || range == "hdr10") return "HDR10"
        val transfer = colorTransfer?.trim()?.lowercase(Locale.US).orEmpty()
        if (transfer.contains("arib") || transfer == "hlg") return "HLG"
        if (transfer.contains("2084") || transfer == "pq" || transfer.contains("smpte2084")) return "HDR10"
        if (range == "dv" || range.contains("dolby")) return "杜比视界"
        if (range == "hdr") return "HDR10"
        val structuredBlank = rangeType.isNullOrBlank() && extendedType.isNullOrBlank() &&
            videoRange.isNullOrBlank() && colorTransfer.isNullOrBlank()
        if (!structuredBlank) return null
        val hint = displayTitle?.lowercase(Locale.US).orEmpty()
        return when {
            hint.contains("dolby vision") || hint.contains("杜比视界") || hint.contains("dovi") -> "杜比视界"
            hint.contains("hlg") -> "HLG"
            hint.contains("hdr10") -> "HDR10"
            else -> null
        }
    }

    fun sourceFacts(item: BaseItemDto, source: MediaSourceInfoDto?): Triple<String?, String?, String?> {
        val runtime = runtimeLabel(runtimeTicksFor(item, source))
        val resolution = if (source != null) resolutionChip(source) else resolutionChip(item)
        val audio = if (source != null) audioChip(source) ?: audioChip(item) else audioChip(item)
        return Triple(runtime, resolution, audio)
    }

    /** The label and the downward menu cue appear together, only while focused. */
    fun versionShowsLabel(focused: Boolean): Boolean = focused

    /** Same glyph as the player's multi-version button. The stacked diamond is retired. */
    fun versionUsesPlayerIcon(): Boolean = true

    fun versionMenuHint(focused: Boolean): Boolean = focused

    fun versionControl(sourceCount: Int, focused: Boolean, menuOpen: Boolean): VersionControl {
        if (!showVersionButton(sourceCount)) return VersionControl.Hidden
        if (menuOpen) return VersionControl.Menu
        if (focused && versionShowsLabel(focused)) return VersionControl.Label
        return VersionControl.Icon
    }

    fun collectionRowTitle(name: String): String = "${name}中的其他作品"

    fun collectionWorks(members: List<BaseItemDto>, currentId: String?): List<BaseItemDto> {
        return members.filter { !it.id.isNullOrBlank() && it.id != currentId }
    }

    fun isBoxSet(item: BaseItemDto): Boolean = item.type.equals("BoxSet", true)

    /**
     * Ancestors alone missed box sets on the 2.0.39 projector test. Membership
     * is the union of every source [collectionLookupSteps] names.
     */
    fun collectionLookupSteps(): List<String> = listOf(
        "users-ancestors",
        "item-ancestors",
        "collection-ids",
        "parent-boxset",
        "boxset-catalog-membership",
        "boxset-child-scan",
    )

    fun boxContainsTitle(memberIds: List<String?>, currentId: String?): Boolean {
        if (currentId.isNullOrBlank()) return false
        return memberIds.any { it == currentId }
    }

    /** A row is bound only when it has other titles. An empty list leaves no slot. */
    fun visibleCollectionRows(
        rows: List<Pair<BaseItemDto, List<BaseItemDto>>>,
    ): List<Pair<BaseItemDto, List<BaseItemDto>>> = rows.filter { it.second.isNotEmpty() }

    fun mergeCollectionRows(
        ancestorBoxes: List<BaseItemDto>,
        membershipBoxes: List<BaseItemDto>,
        membersOf: (String) -> List<BaseItemDto>,
        currentId: String?,
    ): List<Pair<BaseItemDto, List<BaseItemDto>>> {
        val boxes = linkedMapOf<String, BaseItemDto>()
        for (box in ancestorBoxes + membershipBoxes) {
            if (!isBoxSet(box)) continue
            val id = box.id ?: continue
            boxes.putIfAbsent(id, box)
        }
        return boxes.values.mapNotNull { box ->
            val id = box.id ?: return@mapNotNull null
            val others = collectionWorks(membersOf(id), currentId)
            if (others.isEmpty()) null else box to others
        }
    }

    fun viewAllLabel(): String = "查看全部"

    fun showsViewAllTile(view: BaseItemDto): Boolean = isPosterRowLibrary(view)

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
    fun libraryTileSource(hasPrimary: Boolean, collageCount: Int): String = when {
        hasPrimary -> "primary"
        collageCount > 0 -> "collage"
        else -> "placeholder"
    }

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
