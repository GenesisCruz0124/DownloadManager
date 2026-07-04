package com.genesiscruz.downloadmanager.service

import android.app.DownloadManager
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/** A download owned by the Android system DownloadManager (another app started it). */
data class SystemDownload(
    val id: Long,
    val title: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: Int,
    val localUri: String?,
    val mediaType: String?
) {
    val statusLabel: String
        get() = when (status) {
            DownloadManager.STATUS_PENDING -> "Pending"
            DownloadManager.STATUS_RUNNING -> "Running"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
            DownloadManager.STATUS_FAILED -> "Failed"
            else -> "Unknown"
        }
}

/**
 * Observes the system DownloadManager queue so downloads started by other
 * apps show up in our list. These entries are read-only: Android offers no
 * API to pause or accelerate another app's downloads.
 */
@Singleton
class SystemDownloadObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val downloads: Flow<List<SystemDownload>> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(query())
            }
        }
        trySend(query())
        // The my_downloads URI notifies for rows visible to this app; the query
        // below goes through the public DownloadManager API.
        context.contentResolver.registerContentObserver(
            Uri.parse("content://downloads/my_downloads"), true, observer
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.flowOn(Dispatchers.IO)

    fun query(): List<SystemDownload> {
        val result = mutableListOf<SystemDownload>()
        runCatching {
            downloadManager.query(DownloadManager.Query())?.use { cursor ->
                val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val titleCol = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val doneCol =
                    cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val uriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val typeCol = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    result += SystemDownload(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol) ?: "Download",
                        totalBytes = cursor.getLong(totalCol),
                        downloadedBytes = cursor.getLong(doneCol),
                        status = cursor.getInt(statusCol),
                        localUri = cursor.getString(uriCol),
                        mediaType = cursor.getString(typeCol)
                    )
                }
            }
        }
        return result
    }
}
