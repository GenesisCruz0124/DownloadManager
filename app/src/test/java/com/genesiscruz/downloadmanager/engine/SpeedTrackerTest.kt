package com.genesiscruz.downloadmanager.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTrackerTest {

    @Test
    fun `computes speed over the rolling window`() {
        val tracker = SpeedTracker(windowMillis = 3_000)
        tracker.onBytes(0, nowMillis = 0)
        tracker.onBytes(1_000_000, nowMillis = 1_000)
        assertEquals(1_000_000, tracker.bytesPerSecond)
    }

    @Test
    fun `drops samples older than the window`() {
        val tracker = SpeedTracker(windowMillis = 3_000)
        tracker.onBytes(0, nowMillis = 0)
        tracker.onBytes(1_000, nowMillis = 1_000)
        // 10s later: the old fast samples must no longer influence the speed.
        tracker.onBytes(2_000, nowMillis = 10_000)
        tracker.onBytes(3_000, nowMillis = 11_000)
        assertEquals(1_000, tracker.bytesPerSecond)
    }

    @Test
    fun `eta uses remaining bytes and current speed`() {
        val tracker = SpeedTracker(windowMillis = 3_000)
        tracker.onBytes(0, nowMillis = 0)
        tracker.onBytes(2_000, nowMillis = 1_000)
        assertEquals(4, tracker.etaSeconds(downloadedBytes = 2_000, totalBytes = 10_000))
    }

    @Test
    fun `eta is unknown without speed or size`() {
        val tracker = SpeedTracker()
        assertEquals(-1, tracker.etaSeconds(0, 100))
        tracker.onBytes(0, nowMillis = 0)
        tracker.onBytes(500, nowMillis = 1_000)
        assertEquals(-1, tracker.etaSeconds(500, -1))
    }
}
