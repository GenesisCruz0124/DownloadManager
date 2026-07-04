package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.Collections
import kotlin.random.Random

/**
 * Serves [content] with correct HTTP Range / If-Range semantics so the full
 * segmented download flow can run against a real HTTP stack.
 */
private class RangeDispatcher(
    private val content: ByteArray,
    private val etag: String? = "\"v1\"",
    private val supportRanges: Boolean = true
) : Dispatcher() {

    val rangeHeaders = Collections.synchronizedList(mutableListOf<String?>())

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.method == "HEAD") {
            // Force the probe down the ranged-GET path, which is the more
            // interesting one to exercise.
            return MockResponse().setResponseCode(405)
        }
        rangeHeaders.add(request.getHeader("Range"))

        val range = request.getHeader("Range")
        if (range != null && supportRanges) {
            val ifRange = request.getHeader("If-Range")
            if (ifRange == null || etag == null || ifRange == etag) {
                val match = Regex("""bytes=(\d+)-(\d*)""").find(range)
                    ?: return MockResponse().setResponseCode(416)
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toIntOrNull() ?: (content.size - 1)
                if (start >= content.size) return MockResponse().setResponseCode(416)
                val body = content.copyOfRange(start, minOf(end, content.size - 1) + 1)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-${start + body.size - 1}/${content.size}")
                    .apply { etag?.let { setHeader("ETag", it) } }
                    .setBody(Buffer().write(body))
            }
            // If-Range validation failed: fall through to a full 200 response.
        }
        return MockResponse()
            .setResponseCode(200)
            .apply { etag?.let { setHeader("ETag", it) } }
            .setBody(Buffer().write(content))
    }
}

class DownloadTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repo: DownloadRepository
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        repo = DownloadRepository(FakeDownloadDao())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun content(size: Int): ByteArray = Random(42).nextBytes(size)

    private suspend fun newDownload(url: String): DownloadEntity {
        val temp = File(tempFolder.root, "test.part")
        val entity = DownloadEntity(url = url, fileName = "test.bin", tempFilePath = temp.absolutePath)
        val id = repo.insert(entity)
        val withId = entity.copy(id = id)
        repo.update(withId)
        return withId
    }

    @Test
    fun `multi segment download assembles the file byte-identically`() = runTest {
        val data = content(4 * 1024 * 1024)
        val dispatcher = RangeDispatcher(data)
        server.dispatcher = dispatcher
        server.start()

        val download = newDownload(server.url("/file.bin").toString())
        val task = DownloadTask(client, repo, progressFlushMillis = 50)
        val result = withContext(Dispatchers.Default) { task.run(download, requestedSegments = 4) }

        assertArrayEquals(data, File(result.tempFilePath).readBytes())
        assertEquals(data.size.toLong(), result.totalBytes)
        assertTrue(result.supportsRanges)
        assertEquals(4, repo.getSegments(download.id).size)
        // Probe (bytes=0-0) + one ranged request per segment.
        val segmentRanges = dispatcher.rangeHeaders.drop(1)
        assertEquals(4, segmentRanges.size)
        assertTrue(segmentRanges.all { it != null && it.startsWith("bytes=") })
    }

    @Test
    fun `server without range support falls back to single connection`() = runTest {
        val data = content(3 * 1024 * 1024)
        server.dispatcher = RangeDispatcher(data, supportRanges = false)
        server.start()

        val download = newDownload(server.url("/file.bin").toString())
        val task = DownloadTask(client, repo, progressFlushMillis = 50)
        val result = withContext(Dispatchers.Default) { task.run(download, requestedSegments = 8) }

        assertArrayEquals(data, File(result.tempFilePath).readBytes())
        assertFalse(result.supportsRanges)
        assertEquals(1, repo.getSegments(download.id).size)
    }

    @Test
    fun `resume continues from persisted offsets with correct range requests`() = runTest {
        val data = content(4 * 1024 * 1024)
        val dispatcher = RangeDispatcher(data)
        server.dispatcher = dispatcher
        server.start()

        // Simulate a paused download: metadata probed, segments planned, and
        // the first half of every segment already written to disk.
        var download = newDownload(server.url("/file.bin").toString())
        download = download.copy(
            totalBytes = data.size.toLong(),
            supportsRanges = true,
            etag = "\"v1\"",
            segmentCount = 4
        )
        repo.update(download)
        val planned = Segmentation.plan(download.id, data.size.toLong(), true, 4)
        val file = File(download.tempFilePath)
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(data.size.toLong())
            planned.forEach { segment ->
                val half = (segment.end - segment.start + 1) / 2
                raf.seek(segment.start)
                raf.write(data, segment.start.toInt(), half.toInt())
            }
        }
        repo.resetSegments(
            download.id,
            planned.map { it.copy(downloadedBytes = (it.end - it.start + 1) / 2) }
        )

        val task = DownloadTask(client, repo, progressFlushMillis = 50)
        val result = withContext(Dispatchers.Default) { task.run(download, requestedSegments = 4) }

        assertArrayEquals(data, File(result.tempFilePath).readBytes())
        // No probe this time: every request must be a ranged segment request
        // starting at start + downloadedBytes, none of them from offset 0.
        assertEquals(4, dispatcher.rangeHeaders.size)
        planned.forEach { segment ->
            val expectedFrom = segment.start + (segment.end - segment.start + 1) / 2
            assertTrue(
                "expected a request resuming at $expectedFrom",
                dispatcher.rangeHeaders.any { it == "bytes=$expectedFrom-${segment.end}" }
            )
        }
    }

    @Test
    fun `changed content on the server restarts the download from scratch`() = runTest {
        val data = content(3 * 1024 * 1024)
        // Server now holds v2, but our stored validator says v1: the first
        // ranged request comes back as 200 and the task must restart cleanly.
        val dispatcher = RangeDispatcher(data, etag = "\"v2\"")
        server.dispatcher = dispatcher
        server.start()

        var download = newDownload(server.url("/file.bin").toString())
        download = download.copy(
            totalBytes = data.size.toLong(),
            supportsRanges = true,
            etag = "\"v1\"",
            segmentCount = 4
        )
        repo.update(download)
        val planned = Segmentation.plan(download.id, data.size.toLong(), true, 4)
        RandomAccessFile(File(download.tempFilePath), "rw").use {
            it.setLength(data.size.toLong())
        }
        repo.resetSegments(
            download.id,
            planned.map { it.copy(downloadedBytes = 1024) }
        )

        val task = DownloadTask(client, repo, progressFlushMillis = 50)
        val result = withContext(Dispatchers.Default) { task.run(download, requestedSegments = 4) }

        assertArrayEquals(data, File(result.tempFilePath).readBytes())
        assertEquals("\"v2\"", repo.getById(download.id)?.etag)
    }

    @Test
    fun `probe detects size and range support via ranged get`() = runTest {
        val data = content(1024)
        server.dispatcher = RangeDispatcher(data)
        server.start()

        val task = DownloadTask(client, repo)
        val probe = task.probe(server.url("/file.bin").toString())

        assertEquals(1024L, probe.totalBytes)
        assertTrue(probe.supportsRanges)
        assertEquals("\"v1\"", probe.etag)
        assertEquals("file.bin", probe.fileName)
    }
}
