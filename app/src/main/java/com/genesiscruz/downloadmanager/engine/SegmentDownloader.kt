package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/** Thrown when an If-Range validation fails: the remote content changed. */
class ContentChangedException : IOException("Remote content changed since the download started")

/**
 * Downloads one byte range of a file over a single HTTP connection, writing
 * directly into the correct region of the shared target file.
 */
class SegmentDownloader(
    private val client: OkHttpClient,
    private val url: String,
    private val file: File,
    private val etag: String?,
    private val bufferSize: Int = 64 * 1024,
    private val maxRetries: Int = 3,
    private val retryDelayMillis: Long = 1_000
) {

    /**
     * Downloads [segment] starting after its already-downloaded bytes.
     * Calls [onBytes] with the delta byte count as data arrives.
     * Retries transient IO failures with exponential backoff; rethrows after
     * [maxRetries] attempts.
     */
    suspend fun download(segment: SegmentEntity, onBytes: (delta: Long) -> Unit) {
        var downloaded = segment.downloadedBytes
        var attempt = 0
        while (true) {
            try {
                downloadOnce(segment, downloaded) { delta ->
                    downloaded += delta
                    onBytes(delta)
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: ContentChangedException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt > maxRetries) throw e
                delay(retryDelayMillis * (1L shl (attempt - 1)))
            }
        }
    }

    private suspend fun downloadOnce(
        segment: SegmentEntity,
        alreadyDownloaded: Long,
        onBytes: (delta: Long) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val isBounded = segment.end >= 0
        val from = segment.start + alreadyDownloaded
        if (isBounded && from > segment.end) return@withContext

        val requestBuilder = Request.Builder().url(url)
        val expectPartial = isBounded || from > 0
        if (expectPartial) {
            val rangeValue = if (isBounded) "bytes=$from-${segment.end}" else "bytes=$from-"
            requestBuilder.header("Range", rangeValue)
            if (etag != null) requestBuilder.header("If-Range", etag)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (expectPartial && response.code == 200) {
                if (alreadyDownloaded > 0) {
                    // We had bytes on disk and expected to resume from them,
                    // but the server sent the whole entity instead: either the
                    // remote content changed, or ranges stopped being honored
                    // mid-resume. Either way the safe move is to restart.
                    throw ContentChangedException()
                }
                // A fresh request expected a partial response (probe said this
                // host supports ranges) but got the whole entity instead. Some
                // hosts advertise range support they don't actually honor on
                // GET. The body starts at absolute file offset 0, so skip
                // ahead to our segment's start before writing its slice.
                writeBody(response, writeFrom = from, skipBytes = from, maxBytes = segmentLimit(segment), onBytes)
                return@use
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            writeBody(response, writeFrom = from, skipBytes = 0, maxBytes = Long.MAX_VALUE, onBytes)
        }
    }

    private fun segmentLimit(segment: SegmentEntity): Long =
        if (segment.end >= 0) segment.end - segment.start + 1 else Long.MAX_VALUE

    private suspend fun writeBody(
        response: Response,
        writeFrom: Long,
        skipBytes: Long,
        maxBytes: Long,
        onBytes: (delta: Long) -> Unit
    ) {
        val body = response.body ?: throw IOException("Empty body for $url")
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(writeFrom)
            val buffer = ByteArray(bufferSize)
            body.byteStream().use { input ->
                var remainingSkip = skipBytes
                while (remainingSkip > 0) {
                    coroutineContext.ensureActive()
                    val skipped = input.skip(remainingSkip)
                    if (skipped > 0) {
                        remainingSkip -= skipped
                        continue
                    }
                    // Some streams' skip() is a no-op; fall back to read-and-discard.
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remainingSkip).toInt())
                    if (read == -1) break
                    remainingSkip -= read
                }

                var written = 0L
                while (written < maxBytes) {
                    val toRead = minOf(buffer.size.toLong(), maxBytes - written).toInt()
                    coroutineContext.ensureActive()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    raf.write(buffer, 0, read)
                    written += read
                    onBytes(read.toLong())
                }
            }
        }
    }
}
