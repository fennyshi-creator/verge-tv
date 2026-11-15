package com.xxxx.emby_tv.ui.motion

import androidx.compose.runtime.mutableStateOf
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.remote.ancestorsPath
import com.xxxx.emby_tv.data.remote.boxSetCatalogPath
import com.xxxx.emby_tv.data.remote.boxSetMembershipPath
import com.xxxx.emby_tv.data.remote.itemAncestorsPath
import com.xxxx.emby_tv.data.remote.latestItemsPath
import com.xxxx.emby_tv.data.remote.libraryItemsPath
import com.xxxx.emby_tv.data.remote.parseItemListJson
import com.xxxx.emby_tv.data.remote.seriesAddedPath
import com.xxxx.emby_tv.ui.home.ArtworkWash
import com.xxxx.emby_tv.ui.home.HomeMask
import com.xxxx.emby_tv.ui.home.HomeStyle
import com.xxxx.emby_tv.ui.motion.FocusLift
import com.xxxx.emby_tv.ui.motion.FocusRestore
import com.xxxx.emby_tv.ui.motion.Motion
import com.xxxx.emby_tv.ui.motion.StageDock
import com.xxxx.emby_tv.ui.home.PageLayers
import com.xxxx.emby_tv.ui.home.PosterRowFit
import com.xxxx.emby_tv.ui.SettingsCatalog
import com.xxxx.emby_tv.util.CollectionIndex
import com.xxxx.emby_tv.util.EmbyPresentation
import com.xxxx.emby_tv.util.LibraryFocus
import com.xxxx.emby_tv.util.VersionControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishTest {
    @Test
    fun heroMetaOmitsMissingPieces() {
        assertEquals(
            "剧情 · 2024 · ★ 8.1",
            EmbyPresentation.heroMetaLine(listOf("剧情"), 2024, 8.14),
        )
        assertEquals("2024", EmbyPresentation.heroMetaLine(emptyList(), 2024, null))
        assertEquals("剧情 · ★ 7.0", EmbyPresentation.heroMetaLine(listOf("剧情", ""), null, 7.0))
        assertEquals("", EmbyPresentation.heroMetaLine(null, null, 0.0))
    }

    @Test
    fun firstCardHasNoLeftPeekAndLaterCardsDo() {
        val inset = 32
        val gap = 14
        val stride = 138
        assertEquals(0, LeadingPeek.peekWidth(0, inset, gap))
        assertEquals(inset, LeadingPeek.slide(0, inset, stride))
        assertEquals(inset - gap, LeadingPeek.peekWidth(2, inset, gap))
        assertTrue(LeadingPeek.peekWidth(2, inset, gap) > 0)
        assertEquals(inset, LeadingPeek.focusedX(inset, slot = 0, stridePx = stride))
        val window = 2
        val focused = LeadingPeek.slide(window, inset, stride) + window * stride
        assertEquals(inset, focused)
    }

    @Test
    fun seasonPillIsGrayUntilTheTabsHaveFocus() {
        assertEquals(FocusPill.Face.Selected, FocusPill.face(rowFocused = false, current = true))
        assertEquals(FocusPill.Face.Focused, FocusPill.face(rowFocused = true, current = true))
        assertEquals(FocusPill.Face.Idle, FocusPill.face(rowFocused = true, current = false))
        assertEquals(FocusPill.Face.Idle, FocusPill.face(rowFocused = false, current = false))
    }

    @Test
    fun menusStayGrayUntilFocusIsOnThem() {
        assertEquals(FocusPill.Face.Idle, FocusPill.menuFace(menuFocused = false, slot = 1, index = 1))
        assertEquals(FocusPill.Face.Focused, FocusPill.menuFace(menuFocused = true, slot = 1, index = 1))
        assertEquals(FocusPill.Face.Idle, FocusPill.menuFace(menuFocused = true, slot = 1, index = 0))
    }

    @Test
    fun washFallsBackWhenNoSwatch() {
        assertNull(ArtworkWash.argb(null, null, null))
        assertEquals(0x112233, ArtworkWash.argb(0x112233, 0x445566, null))
        assertEquals(0x445566, ArtworkWash.argb(null, 0x445566, 0x778899))
        assertNull(ArtworkWash.sampleMuted(IntArray(0)))
        assertNull(ArtworkWash.sampleMuted(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))
        val muted = ArtworkWash.sampleMuted(intArrayOf(0xFF3A2430.toInt(), 0xFF4A3040.toInt(), 0xFF2A2030.toInt()))
        assertTrue(muted != null)
    }

    @Test
    fun heroRequestIncludesLogoOverviewAndGenres() {
        val latest = latestItemsPath("user", "3", "Movie", 16, "token")
        assertTrue(latest.contains("Overview"))
        assertTrue(latest.contains("Genres"))
        assertTrue(latest.contains("CommunityRating"))
        assertTrue(latest.contains("EnableImageTypes=Primary,Backdrop,Thumb,Logo"))
        val series = seriesAddedPath("user", "237", 16, "token")
        assertTrue(series.contains("Overview"))
        assertTrue(series.contains("Logo"))
        assertTrue(EmbyPresentation.hasLogo(BaseItemDto(id = "1", imageTags = mapOf("Logo" to "tag"))))
        assertTrue(!EmbyPresentation.hasLogo(BaseItemDto(name = "只有名字", productionYear = 2024)))
        val withOverview = BaseItemDto(name = "片名", productionYear = 2024, overview = "一段简介", genres = listOf("剧情"))
        assertEquals("剧情 · 2024", EmbyPresentation.heroMetaLine(withOverview.genres, withOverview.productionYear, null))
        assertTrue(!withOverview.overview.isNullOrBlank())
    }

    @Test
    fun posterRowShowsAboutSixAndAHalf() {
        val count = PosterRowFit.visibleCount(viewport = 960, inset = 32, card = 124, gap = 20)
        assertTrue(count in 6.3f..6.7f)
    }

    @Test
    fun libraryGridIsSixWideAndFiltersHitTheApi() {
        val url = libraryItemsPath(
            "user", "3", "Series", 0, 36, "token",
            genre = "动作,喜剧",
            sortBy = "DateLastContentAdded",
            filters = "IsUnplayed",
            officialRatings = "PG-13",
            seriesStatus = "Continuing",
            minYear = 2020,
            maxYear = 2029,
        )
        assertTrue(url.contains("SortBy=DateLastContentAdded"))
        assertTrue(url.contains("Filters=IsUnplayed"))
        assertTrue(url.contains("OfficialRatings=PG-13"))
        assertTrue(url.contains("SeriesStatus=Continuing"))
        assertTrue(url.contains("MinPremiereDate=2020-01-01"))
        assertTrue(url.contains("MaxPremiereDate=2029-12-31"))
        assertTrue(url.contains("EndDate"))
        assertTrue(url.contains("Genres="))
        assertEquals(0, StageDock.libraryRowOffset(400, gridRow = 0, stagePx = 160, maxScroll = 3000))
        assertEquals(740, StageDock.libraryRowOffset(900, gridRow = 2, stagePx = 160, maxScroll = 3000))
        assertEquals(6, (0 until 36).chunked(6).first().size)
    }

    @Test
    fun versionButtonHidesForOneSourceAndLabelFollowsFocus() {
        assertTrue(!EmbyPresentation.showVersionButton(0))
        assertTrue(!EmbyPresentation.showVersionButton(1))
        assertTrue(EmbyPresentation.showVersionButton(2))
        assertTrue(!EmbyPresentation.versionShowsLabel(false))
        assertTrue(EmbyPresentation.versionShowsLabel(true))
    }

    @Test
    fun collectionRowDropsTheCurrentTitle() {
        val members = listOf(
            BaseItemDto(id = "self", name = "奥德赛"),
            BaseItemDto(id = "other", name = "另一部"),
        )
        assertEquals("合集中的其他作品", EmbyPresentation.collectionRowTitle("合集"))
        assertEquals(listOf("other"), EmbyPresentation.collectionWorks(members, "self").map { it.id })
        assertTrue(EmbyPresentation.collectionWorks(listOf(BaseItemDto(id = "self")), "self").isEmpty())
    }

    @Test
    fun settingsCategoriesStayInsideExistingFeatures() {
        assertTrue(SettingsCatalog.categories.none { it.contains("弹幕") })
        assertTrue(SettingsCatalog.allows("播放"))
        assertTrue(SettingsCatalog.allows("字幕"))
        assertTrue(!SettingsCatalog.allows("弹幕设置"))
    }

    @Test
    fun versionControlIsIconThenLabelThenMenu() {
        assertEquals(VersionControl.Hidden, EmbyPresentation.versionControl(1, focused = true, menuOpen = false))
        assertEquals(VersionControl.Icon, EmbyPresentation.versionControl(2, focused = false, menuOpen = false))
        assertEquals(VersionControl.Label, EmbyPresentation.versionControl(2, focused = true, menuOpen = false))
        assertEquals(VersionControl.Menu, EmbyPresentation.versionControl(2, focused = true, menuOpen = true))
        assertTrue(EmbyPresentation.versionUsesPlayerIcon())
        assertTrue(EmbyPresentation.versionMenuHint(focused = true))
        assertTrue(!EmbyPresentation.versionMenuHint(focused = false))
        assertTrue(!EmbyPresentation.versionShowsLabel(false))
        val source = com.xxxx.emby_tv.data.model.MediaSourceInfoDto(
            name = "4K",
            bitrate = 192,
        )
        val text = EmbyPresentation.versionButtonLabel(source)
        assertEquals("2160P", text)
        assertTrue(!text.contains("kbps"))
        val hd = com.xxxx.emby_tv.data.model.MediaSourceInfoDto(name = "1080p")
        assertEquals("1080P", EmbyPresentation.versionButtonLabel(hd))
    }

    @Test
    fun detailsHeroPeeksASliverAndUsesTheHomeEase() {
        val viewport = 1080
        val peek = StageDock.FIRST_PEEK_DP
        val hero = StageDock.heroBlockPx(viewport, peek)
        assertEquals(1040, hero)
        assertTrue(StageDock.peekIsSliver(peek, nextBlockPx = 220))
        assertEquals(0f, StageDock.bringIntoViewDistance())
        assertEquals(0, StageDock.offset(rowTop = 900, index = 0, stagePx = 80, maxScroll = 2000))
        assertEquals(820, StageDock.offset(rowTop = 900, index = 1, stagePx = 80, maxScroll = 2000))
        assertEquals(Motion.pageMs, 380)
    }

    @Test
    fun collectionLookupIsNotOnlyAncestors() {
        val steps = EmbyPresentation.collectionLookupSteps()
        assertTrue(steps.contains("users-ancestors"))
        assertTrue(steps.contains("boxset-catalog-membership"))
        assertTrue(steps.contains("collection-ids"))
        assertTrue(steps.size > 1)
        val box = BaseItemDto(id = "box", name = "合集", type = "BoxSet")
        val rows = EmbyPresentation.mergeCollectionRows(
            ancestorBoxes = emptyList(),
            membershipBoxes = listOf(box),
            membersOf = {
                listOf(BaseItemDto(id = "self"), BaseItemDto(id = "other", name = "另一部"))
            },
            currentId = "self",
        )
        assertEquals("合集中的其他作品", EmbyPresentation.collectionRowTitle(rows.single().first.name.orEmpty()))
        assertEquals(listOf("other"), rows.single().second.map { it.id })
        val wrapped = parseItemListJson("""{"Items":[{"Id":"box","Type":"BoxSet","Name":"合集"}]}""")
        assertEquals("BoxSet", wrapped.single().type)
        val raw = parseItemListJson("""[{"Id":"box","Type":"BoxSet"}]""")
        assertEquals("box", raw.single().id)
        assertTrue(ancestorsPath("u", "item", "k").contains("/Ancestors"))
        assertTrue(itemAncestorsPath("item", "u", "k").contains("/Items/item/Ancestors"))
        assertTrue(boxSetCatalogPath("u", "k").contains("IncludeItemTypes=BoxSet"))
        assertTrue(boxSetMembershipPath("u", "box", "item", "k").contains("ParentId=box"))
        assertTrue(!boxSetMembershipPath("u", "box", "item", "k").contains("Ids=item"))
        assertTrue(EmbyPresentation.boxContainsTitle(listOf("self", "other"), "self"))
        val bound = EmbyPresentation.visibleCollectionRows(
            listOf(box to listOf(BaseItemDto(id = "other", name = "另一部"))),
        )
        assertEquals(1, bound.size)
        assertTrue(EmbyPresentation.visibleCollectionRows(listOf(box to emptyList())).isEmpty())
    }

    @Test
    fun washIsDrawnAfterTheDarkFill() {
        assertTrue(PageLayers.washAboveFill(PageLayers.home))
        assertTrue(PageLayers.washAboveFill(PageLayers.library))
        assertTrue(PageLayers.washAboveFill(PageLayers.details))
        assertEquals(0f, ArtworkWash.strength(onHero = true, hasArtwork = true))
        assertEquals(1f, ArtworkWash.strength(onHero = false, hasArtwork = true))
        assertEquals(0f, ArtworkWash.strength(onHero = false, hasArtwork = false))
        assertEquals(1f, HomeMask.stillAlpha(0f))
        assertEquals(0f, HomeMask.stillAlpha(1f))
        assertTrue(HomeMask.stillKeepUntil(900, 240) in 0.7f..0.8f)
        assertTrue(HomeMask.seam.value in 200f..320f)
        assertTrue(HomeMask.stillAlpha(0.5f) > 0.5f)
        assertEquals(0, HomeMask.homeScroll(900, 0, 48, 900, 4000))
        assertEquals(900, HomeMask.homeScroll(900, 1, 48, 900, 4000))
        assertFalse(HomeMask.paintsOwnFill("continue"))
        assertFalse(HomeMask.paintsOwnFill("library"))
        assertTrue(HomeMask.sharesWash("continue"))
        assertTrue(HomeMask.sharesWash("library"))
        assertTrue(HomeMask.sharesWash("posters"))
        val darkRed = ArtworkWash.sampleMuted(intArrayOf(0xFF401810.toInt(), 0xFF502018.toInt()))
        assertTrue(darkRed != null)
        assertTrue(ArtworkWash.needsSoftwareCopy("HARDWARE"))
        assertTrue(!ArtworkWash.needsSoftwareCopy("ARGB_8888"))
        val warm = ArtworkWash.sampleMuted(intArrayOf(0xFF8A3030.toInt(), 0xFF703028.toInt()))
        val cool = ArtworkWash.sampleMuted(intArrayOf(0xFF204080.toInt(), 0xFF183868.toInt()))
        assertTrue(warm != null && cool != null && warm != cool)
        assertEquals("poster-b", ArtworkWash.artUrlForFocus(true, "poster-b"))
        assertNull(ArtworkWash.artUrlForFocus(false, "poster-b"))
    }

    @Test
    fun focusedArtworkLiftsAndLibraryRowsEndWithViewAll() {
        assertEquals(1f, FocusLift.LAYOUT_SCALE)
        assertEquals(1f, FocusLift.AVATAR_SCALE)
        assertTrue(FocusLift.SCALE in 1.03f..1.05f)
        assertEquals(180, FocusLift.focusedSlotPx(180))
        assertTrue(FocusLift.liftFits(16, cardPx = 260, risePx = 6, strokePx = 3, scale = FocusLift.SCALE))
        assertTrue(StageDock.CAST_HEAD_PEEK_DP > StageDock.FIRST_PEEK_DP)
        assertTrue(StageDock.CAST_HEAD_PEEK_DP > 60)
        assertTrue(StageDock.CAST_HEAD_PEEK_DP < 60 + 100)
        val width = PosterRowFit.cardWidthPx(viewportPx = 1920, insetPx = 64, gapPx = 40)
        val visible = PosterRowFit.visibleCount(1920, 64, width, 40)
        assertTrue(visible in 6.2f..6.5f)
        assertTrue(width <= (1920 * PosterRowFit.MAX_SLOT_FRACTION).toInt())
        assertEquals(186f, HomeStyle.continueWidth.value)
        val movies = BaseItemDto(id = "lib", name = "电影Movies", type = "CollectionFolder", collectionType = "movies")
        assertTrue(EmbyPresentation.showsViewAllTile(movies))
        assertEquals("查看全部", EmbyPresentation.viewAllLabel())
        assertTrue(!EmbyPresentation.showsViewAllTile(BaseItemDto(type = "CollectionFolder", collectionType = "music")))
    }

    @Test
    fun lateCollectionsKeepTheFocusedRowStill() {
        assertTrue(!StageDock.shiftsWhenCollectionsArrive("hero"))
        assertTrue(!StageDock.shiftsWhenCollectionsArrive("cast"))
        assertTrue(!StageDock.shiftsWhenCollectionsArrive("episodes"))
        assertTrue(StageDock.shiftsWhenCollectionsArrive("similar"))
        assertTrue(StageDock.shiftsWhenCollectionsArrive("box:set"))
        assertEquals(1800, StageDock.keptScroll(scroll = 1400, oldTop = 2000, newTop = 2400))
        assertEquals(1400, StageDock.keptScroll(scroll = 1400, oldTop = 2000, newTop = 2000))
        assertEquals(1400, StageDock.keptScroll(scroll = 1400, oldTop = -1, newTop = 2400))
        val index = CollectionIndex()
        val box = BaseItemDto(id = "box", name = "合集", type = "BoxSet")
        val other = BaseItemDto(id = "other", name = "另一部", type = "Movie")
        assertTrue(index.rowsFor("self", listOf(box)).isEmpty())
        index.rememberWorks("box", listOf(BaseItemDto(id = "self"), other))
        index.rememberChildren("box", listOf("self", "other"))
        assertEquals(listOf("other"), index.rowsFor("self", listOf(box)).single().second.map { it.id })
        val stranger = BaseItemDto(id = "stranger", name = "无关", type = "BoxSet")
        index.rememberCatalog(listOf(box, stranger))
        index.rememberChildren("stranger", listOf("nope"))
        assertEquals(1, index.rowsFor("self", emptyList()).size)
        assertTrue(index.boxesToScan(listOf(box, stranger)).isEmpty())
        val fresh = BaseItemDto(id = "fresh", type = "BoxSet")
        assertEquals(listOf("fresh"), index.boxesToScan(listOf(fresh)).map { it.id })
        assertTrue(!EmbyPresentation.showContinueRow(emptyList()))
    }

    @Test
    fun backRestoresTheSameLibraryAndHomeItem() {
        val restore = FocusRestore(mutableStateOf(null), mutableStateOf(null))
        val items = listOf(BaseItemDto(id = "a"), BaseItemDto(id = "b"), BaseItemDto(id = "c"))
        assertEquals(-1, restore.indexIn(items))
        restore.mark("latest-lib", "c")
        assertEquals("latest-lib", restore.rowKey)
        assertEquals(2, restore.indexIn(items))
        assertEquals(1 to 0, LibraryFocus.cell(6, 6))
        assertEquals(2 to 3, LibraryFocus.cell(15, 6))
        restore.mark("hero", "a")
        assertEquals(0, restore.indexIn(items))
    }

    @Test
    fun filterToggleClearsASelectedValue() {
        fun toggle(current: String?, value: String): String? = if (current == value) null else value
        assertEquals(null, toggle("IsPlayed", "IsPlayed"))
        assertEquals("IsUnplayed", toggle(null, "IsUnplayed"))
    }
}
