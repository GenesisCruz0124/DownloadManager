package com.genesiscruz.downloadmanager.util

import android.webkit.MimeTypeMap

object MimeTypes {
    const val APK = "application/vnd.android.package-archive"

    /**
     * Resolves the MIME type to open [fileName] with. Servers frequently
     * report the wrong (or a generic) Content-Type for APKs — e.g. redirect
     * pages, `application/octet-stream` — so the file extension takes
     * priority over [reported] whenever it maps to something concrete.
     */
    fun resolve(fileName: String, reported: String?): String {
        if (fileName.endsWith(".apk", ignoreCase = true)) return APK
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return when {
            fromExtension != null -> fromExtension
            !reported.isNullOrBlank() && reported != "application/octet-stream" -> reported
            else -> "*/*"
        }
    }
}
