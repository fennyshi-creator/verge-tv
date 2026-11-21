package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorPolicyTest {
    @Test
    fun engineCreateStopsWithoutReopen() {
        val message = "engine-create: mpjni: native_setup: ijkmp_create() failed"
        assertTrue(PlaybackErrorPolicy.stopsWithoutReopen(message))
    }

    @Test
    fun hardwareHdrFailureStopsWithoutSoftwareVideo() {
        assertTrue(PlaybackErrorPolicy.stopsWithoutReopen("video-decode: mediacodec-hdr"))
    }

    @Test
    fun decoderErrorsCanStillRecover() {
        assertFalse(PlaybackErrorPolicy.stopsWithoutReopen("FSPlayer 100/1"))
        assertFalse(PlaybackErrorPolicy.stopsWithoutReopen("prepare"))
    }
}
