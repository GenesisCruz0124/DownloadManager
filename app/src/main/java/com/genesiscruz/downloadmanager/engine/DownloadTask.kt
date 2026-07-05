package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.detect.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

data class ProbeResult(
    val totalBytes: Long,
    val supportsRanges: Boolean,
    val etag: String?,
    val mimeType: String?,
    val fileName: String?
)

/**
 * Runs one download end to end: probes the server, plans segments, downloads
 * them in parallel, and keeps progress persisted so the download can resume
 * after pause, process death, or reboot.
 *
 * Cancellation of the calling coroutine is the pause/cancel mechanism; the
 * final progress flush runs in a [NonCancellable] block.
 */
class DownloadTask(
    private val client: OkHttpClient,
    private val repo: DownloadRepository,
    private val speedTracker: SpeedTracker = SpeedTracker(),
    private val progressFlushMillis: Long = 500
) {

    val speed: SpeedTracker get() = speedTracker

    /**
     * @return the completed [DownloadEntity] (not yet marked COMPLETED in the
     * database — the caller owns status transitions and file finalization).
     */
    suspend fun run(download: DownloadEntity, requestedSegments: Int): DownloadEntity {
        var entity = download
        var segments = repo.getSegments(entity.id)

        // Probe on first run, or re-plan if a previous attempt left no segments.
        if (segments.isEmpty() || entity.totalBytes < 0 && !entity.supportsRanges) {
            val probe = probe(entity.url)
            entity = entity.copy(
                totalBytes = probe.totalBytes,
                supportsRanges = probe.supportsRanges,
                etag = probe.etag,
                mimeType = probe.mimeType ?: entity.mimeType,
                fileName = if (UrlUtils.isFallbackName(entity.fileName) && probe.fileName != null) {
                    probe.fileName
                } else {
                    entity.fileName
                }
            )
            segments = Segmentation.plan(
                entity.id, probe.totalBytes, probe.supportsRanges, requestedSegments
            )
            entity = entity.copy(segmentCount = segments.size, downloadedBytes = 0)
            repo.update(entity)
            repo.resetSegments(entity.id, segments)
            preallocate(File(entity.tempFilePath), entity.totalBytes)
        }

        try {
            downloadSegments(entity, segments)
        } catch (e: ContentChangedException) {
            // Server content changed since we started: restart from zero once.
            entity = restartFromScratch(entity, requestedSegments)
            segments = repo.getSegments(entity.id)
            downloadSegments(entity, segments)
        }

        val finalSize = File(entity.tempFilePath).length()
        entity = entity.copy(
            downloadedBytes = finalSize,
            totalBytes = if (entity.totalBytes < 0) finalSize else entity.totalBytes
        )
        repo.update(entity)
        return entity
    }

    private suspend fun restartFromScratch(
        download: DownloadEntity,
        requestedSegments: Int
    ): DownloadEntity {
        val probe = probe(download.url)
        val entity = download.copy(
            totalBytes = probe.totalBytes,
            supportsRanges = probe.supportsRanges,
            etag = probe.etag,
            downloadedBytes = 0
        )
        val segments = Segmentation.plan(
            entity.id, probe.totalBytes, probe.supportsRanges, requestedSegments
        )
        repo.update(entity.copy(segmentCount = segments.size))
        repo.resetSegments(entity.id, segments)
        preallocate(File(entity.tempFilePath), entity.totalBytes)
        return entity.copy(segmentCount = segments.size)
    }

    private suspend fun downloadSegments(
        entity: DownloadEntity,
        segments: List<SegmentEntity>
    ) = coroutineScope {
        val file = File(entity.tempFilePath)
        val perSegment = AtomicLongArray(segments.map { it.downloadedBytes }.toLongArray())
        val total = AtomicLong(segments.sumOf { it.downloadedBytes })
        speedTracker.reset()

        val flusher = launch {
            try {
                while (isActive) {
                    delay(progressFlushMillis)
                    flushProgress(entity.id, segments, perSegment, total.get())
                }
            } finally {
                withContext(NonCancellable) {
                    flushProgress(entity.id, segments, perSegment, total.get())
                }
            }
        }

        try {
            segments.mapIndexed { i, segment ->
                async(Dispatchers.IO) {
                    val downloader = SegmentDownloader(
                        client = client,
                        url = entity.url,
                        file = file,
                        etag = entity.etag
                    )
                    downloader.download(segment.copy(downloadedBytes = perSegment[i])) { delta ->
                        perSegment.addAndGet(i, delta)
                        speedTracker.onBytes(total.addAndGet(delta))
                    }
                }
            }.awaitAll()
        } finally {
            flusher.cancel()
            flusher.join()
        }
    }

    private suspend fun flushProgress(
        downloadId: Long,
        segments: List<SegmentEntity>,
        perSegment: AtomicLongArray,
        totalBytes: Long
    ) {
        segments.forEachIndexed { i, segment ->
            repo.updateSegmentProgress(downloadId, segment.index, perSegment[i])
        }
        repo.updateProgress(downloadId, totalBytes)
    }

    private fun preallocate(file: File, totalBytes: Long) {
        file.parentFile?.mkdirs()
        if (totalBytes > 0) {
            RandomAccessFile(file, "rw").use { it.setLength(totalBytes) }
        } else {
            file.delete()
            file.createNewFile()
        }
    }

    /**
     * Learns size, range support, validator, and filename. `Accept-Ranges` on a
     * HEAD response is only a hint — some hosts advertise it but don't actually
     * honor `Range` on GET (or redirect somewhere that doesn't). So range
     * support is only ever trusted when a real ranged GET comes back `206`;
     * HEAD is used solely as a cheap Content-Length/filename fallback when the
     * GET response doesn't carry them.
     */
    suspend fun probe(url: String): ProbeResult = withContext(Dispatchers.IO) {
        val head = runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute()
        }.getOrNull()
        val headLength = head?.takeIf { it.isSuccessful }
            ?.header("Content-Length")?.toLongOrNull()
        val headFileName = head?.takeIf { it.isSuccessful }?.let { fileNameFrom(it, url) }
        val headMimeType = head?.takeIf { it.isSuccessful }?.header("Content-Type")
        head?.close()

        val request = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IOException("HTTP ${response.code} probing $url")
            }
            val partial = response.code == 206
            val total = if (partial) {
                // Content-Range: bytes 0-0/12345
                response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: headLength ?: -1
            } else {
                response.header("Content-Length")?.toLongOrNull() ?: headLength ?: -1
            }
            response.body?.close()
            ProbeResult(
                totalBytes = total,
                supportsRanges = partial,
                etag = validator(response),
                mimeType = response.header("Content-Type") ?: headMimeType,
                fileName = fileNameFrom(response, url) ?: headFileName
            )
        }
    }

    private fun validator(response: Response): String? =
        response.header("ETag") ?: response.header("Last-Modified")

    private fun fileNameFrom(response: Response, url: String): String? {
        val disposition = response.header("Content-Disposition")
        if (disposition != null) {
            val name = UrlUtils.fileNameFromContentDisposition(disposition)
            if (!name.isNullOrBlank()) return name
        }
        return UrlUtils.fileNameFromUrl(url)
    }
}
