package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.SegmentEntity

object Segmentation {

    /** Files smaller than this are downloaded with a single connection. */
    const val MIN_SIZE_FOR_SPLIT = 2L * 1024 * 1024

    /**
     * Splits [totalBytes] into at most [requestedSegments] inclusive byte ranges.
     * Returns a single full-range segment when the file is small, size is
     * unknown, or the server does not support ranges.
     */
    fun plan(
        downloadId: Long,
        totalBytes: Long,
        supportsRanges: Boolean,
        requestedSegments: Int
    ): List<SegmentEntity> {
        if (!supportsRanges || totalBytes <= 0) {
            // Unbounded segment: plain GET with no Range header. A bounded
            // range request against a non-range server would get a 200 reply
            // that reads as an If-Range mismatch.
            return listOf(SegmentEntity(downloadId, 0, 0, -1))
        }
        if (totalBytes < MIN_SIZE_FOR_SPLIT || requestedSegments <= 1) {
            return listOf(SegmentEntity(downloadId, 0, 0, totalBytes - 1))
        }
        val count = requestedSegments.coerceIn(1, 16)
        val base = totalBytes / count
        return (0 until count).map { i ->
            val start = i * base
            val end = if (i == count - 1) totalBytes - 1 else (i + 1) * base - 1
            SegmentEntity(downloadId, i, start, end)
        }
    }
}
