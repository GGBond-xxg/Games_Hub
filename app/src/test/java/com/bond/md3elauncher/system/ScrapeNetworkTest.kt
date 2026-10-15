package com.bond.md3elauncher.system

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScrapeNetworkTest {
    @Test fun wholeRequestTimeoutCoversNonInterruptibleResolution() {
        val release = CountDownLatch(1)
        val start = System.nanoTime()
        try {
            try {
                ScrapeNetwork.request(60) {
                    while (release.count > 0) try { release.await() } catch (_: InterruptedException) { }
                }
                fail("Expected overall timeout")
            } catch (expected: java.net.SocketTimeoutException) {
                assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 2)
                assertFalse(expected.message!!.contains("https://"))
            }
        } finally { release.countDown() }
    }
    @Test fun cancellationDoesNotWaitForNativeResolution() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val caller = Thread {
            try { ScrapeNetwork.request { started.countDown(); Thread.sleep(30_000) } }
            catch (_: InterruptedException) { interrupted.countDown() }
        }
        caller.start()
        assertTrue(started.await(2, TimeUnit.SECONDS))
        caller.interrupt()
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        caller.join(1000)
        assertFalse(caller.isAlive)
    }
}
