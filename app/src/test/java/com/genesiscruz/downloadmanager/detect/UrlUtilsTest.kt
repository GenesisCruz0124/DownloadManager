package com.genesiscruz.downloadmanager.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `recognises http and https urls`() {
        assertTrue(UrlUtils.isHttpUrl("https://example.com/file.zip"))
        assertTrue(UrlUtils.isHttpUrl("http://example.com"))
        assertFalse(UrlUtils.isHttpUrl("ftp://example.com/file.zip"))
        assertFalse(UrlUtils.isHttpUrl("not a url"))
        assertFalse(UrlUtils.isHttpUrl("https://"))
    }

    @Test
    fun `extracts url embedded in shared text`() {
        assertEquals(
            "https://example.com/file.zip",
            UrlUtils.extractUrl("Check this out: https://example.com/file.zip - great file!")
        )
        assertNull(UrlUtils.extractUrl("no links here"))
    }

    @Test
    fun `derives file name from url path`() {
        assertEquals(
            "ubuntu-24.04.iso",
            UrlUtils.fileNameFromUrl("https://releases.ubuntu.com/24.04/ubuntu-24.04.iso")
        )
        assertEquals(
            "my file.pdf",
            UrlUtils.fileNameFromUrl("https://example.com/docs/my%20file.pdf")
        )
        assertNull(UrlUtils.fileNameFromUrl("https://example.com/"))
    }

    @Test
    fun `parses content disposition file names`() {
        assertEquals(
            "report.pdf",
            UrlUtils.fileNameFromContentDisposition("""attachment; filename="report.pdf"""")
        )
        assertEquals(
            "report.pdf",
            UrlUtils.fileNameFromContentDisposition("attachment; filename=report.pdf")
        )
        assertEquals(
            "naïve file.txt",
            UrlUtils.fileNameFromContentDisposition(
                "attachment; filename*=UTF-8''na%C3%AFve%20file.txt"
            )
        )
        assertNull(UrlUtils.fileNameFromContentDisposition("inline"))
    }

    @Test
    fun `sanitize strips path separators and control characters`() {
        assertEquals(".._etc_passwd", UrlUtils.sanitize("../etc/passwd"))
        assertEquals("a_b_c", UrlUtils.sanitize("a\\b:c"))
        assertEquals("___", UrlUtils.sanitize("///"))
        assertEquals("download", UrlUtils.sanitize("  "))
        assertEquals("normal-name.zip", UrlUtils.sanitize("normal-name.zip"))
    }
}
