package com.genesiscruz.downloadmanager.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One byte range of a download. [start]..[end] inclusive; [downloadedBytes] counts
 * bytes already written from [start], so the next request resumes at
 * start + downloadedBytes.
 */
@Entity(
    tableName = "segments",
    primaryKeys = ["downloadId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("downloadId")]
)
data class SegmentEntity(
    val downloadId: Long,
    val index: Int,
    val start: Long,
    val end: Long,
    val downloadedBytes: Long = 0
) {
    val isComplete: Boolean get() = downloadedBytes >= end - start + 1
}
