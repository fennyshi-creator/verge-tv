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
    fun audioReleaseStopsWithoutOpeningAnotherTrack() {
        assertTrue(PlaybackErrorPolicy.stopsWithoutReopen("audio-release: previous output still closing"))
    }

    @Test
    fun iecFailureStopsWithoutPretendingDolby() {
        assertTrue(PlaybackErrorPolicy.stopsWithoutReopen("audio-passthrough: iec-unavailable"))
    }

    fun decoderErrorsCanStillRecover() {
        assertFalse(PlaybackErrorPolicy.stopsWithoutReopen("FSPlayer 100/1"))
        assertFalse(PlaybackErrorPolicy.stopsWithoutReopen("prepare"))
    }
}
