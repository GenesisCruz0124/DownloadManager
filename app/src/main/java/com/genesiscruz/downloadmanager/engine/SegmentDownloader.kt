package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

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
                // If-Range validation failed (or ranges stopped being supported):
                // the server returned the whole entity instead of our range.
                throw ContentChangedException()
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IOException("Empty body for $url")

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(from)
                val buffer = ByteArray(bufferSize)
                body.byteStream().use { input ->
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        raf.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
        }
    }
}
