package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.ui.player.PassthroughSync
import com.xxxx.emby_tv.ui.player.StallReopen
import com.xxxx.emby_tv.util.SeasonEpisodeList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Release124Test {
    @Test
    fun passthroughCompensationHoldsThePictureAndSkipsPcm() {
        assertEquals(0, PassthroughSync.normalize(0))
        assertEquals(10, PassthroughSync.normalize(14))
        assertEquals(120, PassthroughSync.normalize(500))
        assertEquals(0, PassthroughSync.stepUp(120))
        assertEquals(20, PassthroughSync.stepUp(10))
        assertEquals(0f, PassthroughSync.extraDelaySeconds(40, passthroughOpen = false), 0.0001f)
        assertEquals(-0.04f, PassthroughSync.extraDelaySeconds(40, passthroughOpen = true), 0.0001f)
        assertTrue(PassthroughSync.extraDelaySeconds(40, passthroughOpen = true) < 0f)
        assertEquals(0f, PassthroughSync.extraDelaySeconds(0, passthroughOpen = true), 0.0001f)
        assertFalse(PassthroughSync.isOpen(encoding = 0, fellBackToPcm = false))
        assertFalse(PassthroughSync.isOpen(encoding = 13, fellBackToPcm = true))
        assertTrue(PassthroughSync.isOpen(encoding = 13, fellBackToPcm = false))
    }

    @Test
    fun stallDoesNotReopenWhileFramesContinue() {
        assertFalse(
            StallReopen.shouldReopen(
                clockAdvanced = false,
                framesAdvanced = true,
                stalledLongEnough = true,
                retriesRemaining = true,
            ),
        )
        assertFalse(
            StallReopen.shouldReopen(
                clockAdvanced = true,
                framesAdvanced = false,
                stalledLongEnough = true,
                retriesRemaining = true,
            ),
        )
        assertTrue(
            StallReopen.shouldReopen(
                clockAdvanced = false,
                framesAdvanced = false,
                stalledLongEnough = true,
                retriesRemaining = true,
            ),
        )
        assertFalse(
            StallReopen.shouldReopen(
                clockAdvanced = false,
                framesAdvanced = false,
                stalledLongEnough = false,
                retriesRemaining = true,
            ),
        )
    }

    @Test
    fun episodeStripKeepsTheCurrentSeason() {
        val mixed = listOf(
            episode("a", season = 1, index = 2),
            episode("b", season = 2, index = 1),
            episode("c", season = 1, index = 1),
            episode("d", season = 2, index = 2),
        )
        val fromSeasonQuery = SeasonEpisodeList.forPlayback(
            seasonId = "season-1",
            parentIndexNumber = 1,
            fetched = listOf(episode("c", season = 1, index = 1), episode("a", season = 1, index = 2)),
        )
        assertEquals(listOf("c", "a"), fromSeasonQuery.map { it.id })

        val filtered = SeasonEpisodeList.forPlayback(
            seasonId = null,
            parentIndexNumber = 2,
            fetched = mixed,
        )
        assertEquals(listOf("b", "d"), filtered.map { it.id })
    }

    private fun episode(id: String, season: Int, index: Int) = BaseItemDto(
        id = id,
        parentIndexNumber = season,
        indexNumber = index,
    )
}
