package com.xxxx.emby_tv.ui.player

import com.debugly.fsplayer.audio.FsEncodedAudioTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IecBufferTest {
    @Test
    fun theTrackBufferIsAWholeNumberOfBursts() {
        // 2.0.50 kept getMinBufferSize when it was 65568, which is not a burst.
        val buffer = FsEncodedAudioTrack.alignedBurstBuffer(65568)
        assertEquals(98304, buffer)
        assertEquals(0, buffer % FsEncodedAudioTrack.IEC_BURST_BYTES)
        assertTrue(buffer >= FsEncodedAudioTrack.IEC_BUFFER_BYTES)
    }

    @Test
    fun aLargerMinimumRoundsUp() {
        val buffer = FsEncodedAudioTrack.alignedBurstBuffer(100_000)
        assertEquals(122880, buffer)
        assertEquals(0, buffer % 24576)
    }
}
