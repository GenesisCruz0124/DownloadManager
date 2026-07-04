package com.genesiscruz.downloadmanager.engine

/**
 * Rolling-window download speed and ETA estimator. Thread-safe for a single
 * writer ([onBytes]) and any number of readers.
 */
class SpeedTracker(private val windowMillis: Long = 3_000) {

    private data class Sample(val timeMillis: Long, val totalBytes: Long)

    private val samples = ArrayDeque<Sample>()

    @Volatile
    var bytesPerSecond: Long = 0
        private set

    @Synchronized
    fun onBytes(totalDownloadedBytes: Long, nowMillis: Long = System.currentTimeMillis()) {
        samples.addLast(Sample(nowMillis, totalDownloadedBytes))
        while (samples.size > 1 && nowMillis - samples.first().timeMillis > windowMillis) {
            samples.removeFirst()
        }
        val first = samples.first()
        val elapsed = nowMillis - first.timeMillis
        bytesPerSecond = if (elapsed > 0) {
            (totalDownloadedBytes - first.totalBytes) * 1000 / elapsed
        } else {
            0
        }
    }

    /** Seconds until completion, or -1 when unknown (no speed or unknown size). */
    fun etaSeconds(downloadedBytes: Long, totalBytes: Long): Long {
        val speed = bytesPerSecond
        if (speed <= 0 || totalBytes <= 0) return -1
        return ((totalBytes - downloadedBytes).coerceAtLeast(0)) / speed
    }

    @Synchronized
    fun reset() {
        samples.clear()
        bytesPerSecond = 0
    }
}
