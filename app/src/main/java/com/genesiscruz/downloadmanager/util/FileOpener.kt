package com.genesiscruz.downloadmanager.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import java.io.File

/**
 * Opens a completed download with the system's handler for its MIME type —
 * the Package Installer for APKs, a viewer/editor for everything else.
 */
object FileOpener {

    /**
     * [DownloadEntity.finalUri] is a `content://` MediaStore URI on API 29+,
     * but a plain filesystem path on API 26–28 (legacy storage). A raw
     * `file://` URI would crash with `FileUriExposedException` on API 24+, so
     * legacy paths are wrapped through [FileProvider] to get a content URI.
     */
    fun resolveUri(context: Context, finalUri: String): Uri =
        if (finalUri.startsWith("content://")) {
            Uri.parse(finalUri)
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(finalUri)
            )
        }

    /** @return true if an activity was launched to handle the file. */
    fun open(context: Context, download: DownloadEntity): Boolean {
        val finalUri = download.finalUri ?: return false
        val uri = resolveUri(context, finalUri)
        val mimeType = MimeTypes.resolve(download.fileName, download.mimeType)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
