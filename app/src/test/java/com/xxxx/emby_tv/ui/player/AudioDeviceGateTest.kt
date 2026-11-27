package com.xxxx.emby_tv.ui.player

import org.junit.Assert.assertEquals
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

    @Test
    fun aTimedOutCloseLeavesTheGateIdleForTheNextTitle() {
        val release = java.util.concurrent.CountDownLatch(1)
        AudioDeviceGate.begin { release.await() }
        assertFalse(AudioDeviceGate.awaitIdle(40))
        AudioDeviceGate.forceIdle()
        assertTrue(AudioDeviceGate.awaitIdle(200))
        assertEquals(0, AudioDeviceGate.busyCount())
        AudioDeviceGate.begin { }
        assertTrue(AudioDeviceGate.awaitIdle(1_000))
        release.countDown()
        Thread.sleep(150)
        assertEquals(0, AudioDeviceGate.busyCount())
    }
}