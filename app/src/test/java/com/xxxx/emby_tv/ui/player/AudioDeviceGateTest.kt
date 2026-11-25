package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceGateTest {
    @Test
    fun nextOpenWaitsUntilReleaseFinishes() {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        AudioDeviceGate.begin {
            started.countDown()
            release.await()
        }
        assertTrue(started.await(1, java.util.concurrent.TimeUnit.SECONDS))
        assertFalse(AudioDeviceGate.awaitIdle(50))
        release.countDown()
        assertTrue(AudioDeviceGate.awaitIdle(1_000))
    }

    @Test
    fun aSecondReleaseCanStartAfterTheFirst() {
        AudioDeviceGate.begin { }
        assertTrue(AudioDeviceGate.awaitIdle(1_000))
        AudioDeviceGate.begin { }
        assertTrue(AudioDeviceGate.awaitIdle(1_000))
    }
}