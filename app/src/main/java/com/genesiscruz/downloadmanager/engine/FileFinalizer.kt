package com.genesiscruz.downloadmanager.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.util.MimeTypes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves a finished temp file into the public Downloads collection and returns
 * the URI (API 29+) or file path (API 26–28) the user can open it from.
 */
@Singleton
class FileFinalizer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun publish(download: DownloadEntity): String = withContext(Dispatchers.IO) {
        val temp = File(download.tempFilePath)
        if (!temp.exists()) throw IOException("Temp file missing: ${download.tempFilePath}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(download, temp).toString()
        } else {
            publishToLegacyDownloads(download, temp)
        }
    }

    private fun publishToMediaStore(download: DownloadEntity, temp: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, download.fileName)
            put(MediaStore.Downloads.MIME_TYPE, MimeTypes.resolve(download.fileName, download.mimeType))
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Could not create MediaStore entry for ${download.fileName}")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                temp.inputStream().use { it.copyTo(output) }
            } ?: throw IOException("Could not open output stream for $uri")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        temp.delete()
        return uri
    }

    private fun publishToLegacyDownloads(download: DownloadEntity, temp: File): String {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        var target = File(dir, download.fileName)
        var counter = 1
        while (target.exists()) {
            val base = download.fileName.substringBeforeLast('.', download.fileName)
            val ext = download.fileName.substringAfterLast('.', "")
            val suffixed = if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext"
            target = File(dir, suffixed)
            counter++
        }
        if (!temp.renameTo(target)) {
            temp.inputStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            temp.delete()
        }
        return target.absolutePath
    }
}
