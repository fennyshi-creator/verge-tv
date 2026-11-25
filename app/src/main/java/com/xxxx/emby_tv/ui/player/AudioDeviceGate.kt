package com.xxxx.emby_tv.ui.player

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * One audio device teardown at a time for the whole process. Back and the next
 * title both come through here, so a new track is not opened while the previous
 * encoded track is still being stopped.
 */
object AudioDeviceGate {
    private val lock = ReentrantLock()
    private val idle = lock.newCondition()
    private var busy = 0

    fun begin(work: () -> Unit) {
        lock.withLock { busy += 1 }
        val worker = Thread({
            try {
                work()
            } finally {
                lock.withLock {
                    busy -= 1
                    idle.signalAll()
                }
            }
        }, "verge-audio-release")
        worker.isDaemon = true
        worker.start()
    }

    fun awaitIdle(timeoutMs: Long): Boolean {
        val deadline = System.nanoTime() + timeoutMs.coerceAtLeast(0L) * 1_000_000L
        lock.lock()
        try {
            while (busy > 0) {
                val left = deadline - System.nanoTime()
                if (left <= 0L)
                    return false
                idle.awaitNanos(left)
            }
            return true
        } finally {
            lock.unlock()
        }
    }

    /** Test hook. Production callers use [begin]. */
    internal fun busyCount(): Int = lock.withLock { busy }
}
