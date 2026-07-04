package com.genesiscruz.downloadmanager.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentationTest {

    @Test
    fun `splits large file into requested segments covering every byte`() {
        val total = 10L * 1024 * 1024 + 37
        val segments = Segmentation.plan(1, total, supportsRanges = true, requestedSegments = 8)

        assertEquals(8, segments.size)
        assertEquals(0, segments.first().start)
        assertEquals(total - 1, segments.last().end)
        segments.zipWithNext().forEach { (a, b) ->
            assertEquals("segments must be contiguous", a.end + 1, b.start)
        }
        assertEquals(total, segments.sumOf { it.end - it.start + 1 })
    }

    @Test
    fun `small file gets a single bounded segment`() {
        val segments = Segmentation.plan(1, 100_000, supportsRanges = true, requestedSegments = 8)
        assertEquals(1, segments.size)
        assertEquals(0, segments[0].start)
        assertEquals(99_999, segments[0].end)
    }

    @Test
    fun `no range support gets a single unbounded segment`() {
        val segments =
            Segmentation.plan(1, 50L * 1024 * 1024, supportsRanges = false, requestedSegments = 8)
        assertEquals(1, segments.size)
        assertEquals(-1, segments[0].end)
    }

    @Test
    fun `unknown size gets a single unbounded segment`() {
        val segments = Segmentation.plan(1, -1, supportsRanges = true, requestedSegments = 8)
        assertEquals(1, segments.size)
        assertEquals(-1, segments[0].end)
    }

    @Test
    fun `segment count is clamped to 16`() {
        val segments =
            Segmentation.plan(1, 100L * 1024 * 1024, supportsRanges = true, requestedSegments = 99)
        assertTrue(segments.size <= 16)
    }
}
