package com.genesiscruz.downloadmanager.service

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Any file physically present in the device's public Downloads folder,
 * regardless of which app put it there (this app, a browser, another
 * download manager, a manual copy, etc).
 */
data class FolderFile(
    /** MediaStore row id (API 29+) or absolute path (API 26-28) — stable per file. */
    val key: String,
    val name: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val uri: Uri,
    val mimeType: String?
)

/**
 * Lists every file in the public Downloads folder directly from storage —
 * unlike the in-app download list (Room-tracked) or [SystemDownloadObserver]
 * (Android DownloadManager queue), this reflects the folder's real contents.
 */
@Singleton
class DownloadsFolderObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val files: Flow<List<FolderFile>> = callbackFlow {
        trySend(list())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val handler = Handler(Looper.getMainLooper())
            val observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    trySend(list())
                }
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            context.contentResolver.registerContentObserver(collection, true, observer)
            awaitClose { context.contentResolver.unregisterContentObserver(observer) }
        } else {
            // No practical live-update mechanism for plain filesystem listing
            // pre-scoped-storage; the initial snapshot above is still useful,
            // and the caller can trigger a refresh (e.g. on screen resume).
            awaitClose { }
        }
    }.flowOn(Dispatchers.IO)

    fun list(): List<FolderFile> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) queryMediaStore() else listLegacyFiles()

    private fun queryMediaStore(): List<FolderFile> {
        val result = mutableListOf<FolderFile>()
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.MIME_TYPE
        )
        runCatching {
            context.contentResolver.query(
                collection, projection, null, null,
                "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    result += FolderFile(
                        key = id.toString(),
                        name = cursor.getString(nameCol) ?: "Unknown",
                        sizeBytes = cursor.getLong(sizeCol),
                        dateModifiedMillis = cursor.getLong(dateCol) * 1000,
                        uri = ContentUris.withAppendedId(collection, id),
                        mimeType = cursor.getString(mimeCol)
                    )
                }
            }
        }
        return result
    }

    private fun listLegacyFiles(): List<FolderFile> {
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .map { file -> file.toFolderFile() }
    }

    private fun File.toFolderFile(): FolderFile = FolderFile(
        key = absolutePath,
        name = name,
        sizeBytes = length(),
        dateModifiedMillis = lastModified(),
        uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this),
        mimeType = null
    )

    /** @return true if the file was deleted. */
    fun delete(file: FolderFile): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.delete(file.uri, null, null) > 0 }.getOrDefault(false)
        } else {
            runCatching { File(file.key).delete() }.getOrDefault(false)
        }
}
