package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioReleaseCloseTest {
    @Test
    fun closeWakesTheSampleQueueBeforeWaitingOnTheAudioThread() {
        val file = listOf(
            File("third_party/fsplayer/ijkmedia/ijkplayer/ff_ffplay.c"),
            File("../third_party/fsplayer/ijkmedia/ijkplayer/ff_ffplay.c"),
        ).firstOrNull { it.isFile } ?: error("missing ff_ffplay.c")
        val source = file.readText()
        val close = source.substringAfter("static void stream_close(").substringBefore("static void video_display2")
        val signal = close.indexOf("frame_queue_signal(&is->sampq)")
        val closeAudio = close.indexOf("SDL_AoutCloseAudio(ffp->aout)")
        assertTrue(signal >= 0)
        assertTrue(closeAudio > signal)
        assertTrue(close.indexOf("packet_queue_abort(&is->audioq)") in 0 until signal)
    }
}
