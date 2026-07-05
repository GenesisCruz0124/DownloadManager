package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

/**
 * Covers the defense-in-depth fallback in [SegmentDownloader.downloadOnce]: a
 * host that ignores a Range request it was expected to honor (e.g. a CDN edge
 * that behaves differently per connection than the initial probe suggested).
 */
class SegmentDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fresh segment that gets 200 instead of 206 still completes correctly`() = runTest {
        val data = Random(1).nextBytes(1024)
        // A middle chunk of the file: bytes 256..767. The server ignores the
        // Range header entirely and returns the whole 1024-byte body.
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        server.start()

        val file = File(tempFolder.root, "out.bin").apply {
            createNewFile()
            java.io.RandomAccessFile(this, "rw").use { it.setLength(1024) }
        }
        val downloader = SegmentDownloader(client, server.url("/f").toString(), file, etag = "\"v1\"")
        val segment = SegmentEntity(downloadId = 1, index = 0, start = 256, end = 767)

        withContext(Dispatchers.Default) {
            downloader.download(segment) { }
        }

        // The fallback must write only this segment's slice, at the right offset.
        val written = file.readBytes().copyOfRange(256, 768)
        assertArrayEquals(data.copyOfRange(256, 768), written)
    }

    @Test
    fun `resume that gets 200 instead of 206 is treated as changed content`() = runTest {
        val data = Random(2).nextBytes(1024)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        server.start()

        val file = File(tempFolder.root, "out2.bin").apply {
            createNewFile()
            java.io.RandomAccessFile(this, "rw").use { it.setLength(1024) }
        }
        val downloader = SegmentDownloader(
            client, server.url("/f").toString(), file, etag = "\"v1\"", maxRetries = 0
        )
        // Half of this segment is already "downloaded": a genuine resume.
        val segment = SegmentEntity(downloadId = 1, index = 0, start = 0, end = 1023, downloadedBytes = 512)

        try {
            withContext(Dispatchers.Default) { downloader.download(segment) { } }
            fail("expected ContentChangedException")
        } catch (e: ContentChangedException) {
            // expected
        }
    }
}
