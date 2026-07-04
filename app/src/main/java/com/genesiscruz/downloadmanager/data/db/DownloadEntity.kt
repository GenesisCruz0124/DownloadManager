package com.genesiscruz.downloadmanager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    CONNECTING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    /** Total size in bytes, or -1 when unknown (chunked/no Content-Length). */
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** ETag or Last-Modified used with If-Range to validate resumability. */
    val etag: String? = null,
    val supportsRanges: Boolean = false,
    val segmentCount: Int = 1,
    /** Temp file path while downloading; final content URI / path when completed. */
    val tempFilePath: String,
    val finalUri: String? = null,
    val mimeType: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
