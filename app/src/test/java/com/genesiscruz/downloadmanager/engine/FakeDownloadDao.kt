package com.genesiscruz.downloadmanager.engine

import com.genesiscruz.downloadmanager.data.db.DownloadDao
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DownloadDao for engine tests (no Room, no Android). */
class FakeDownloadDao : DownloadDao {

    private val downloads = MutableStateFlow<Map<Long, DownloadEntity>>(emptyMap())
    private val segments = MutableStateFlow<Map<Long, List<SegmentEntity>>>(emptyMap())
    private var nextId = 1L

    override fun observeAll(): Flow<List<DownloadEntity>> =
        downloads.map { it.values.sortedByDescending { d -> d.createdAt } }

    override fun observeById(id: Long): Flow<DownloadEntity?> = downloads.map { it[id] }

    override suspend fun getById(id: Long): DownloadEntity? = downloads.value[id]

    override suspend fun getByStatus(statuses: List<DownloadStatus>): List<DownloadEntity> =
        downloads.value.values.filter { it.status in statuses }

    override suspend fun getByStatusOrdered(status: DownloadStatus, limit: Int): List<DownloadEntity> =
        downloads.value.values.filter { it.status == status }
            .sortedBy { it.createdAt }
            .take(limit)

    override suspend fun insert(download: DownloadEntity): Long {
        val id = if (download.id == 0L) nextId++ else download.id
        downloads.value = downloads.value + (id to download.copy(id = id))
        return id
    }

    override suspend fun update(download: DownloadEntity) {
        downloads.value = downloads.value + (download.id to download)
    }

    override suspend fun updateStatus(id: Long, status: DownloadStatus, error: String?) {
        downloads.value[id]?.let {
            update(it.copy(status = status, errorMessage = error))
        }
    }

    override suspend fun updateProgress(id: Long, downloadedBytes: Long) {
        downloads.value[id]?.let { update(it.copy(downloadedBytes = downloadedBytes)) }
    }

    override suspend fun delete(id: Long) {
        downloads.value = downloads.value - id
        segments.value = segments.value - id
    }

    override suspend fun getSegments(downloadId: Long): List<SegmentEntity> =
        segments.value[downloadId].orEmpty()

    override fun observeSegments(downloadId: Long): Flow<List<SegmentEntity>> =
        segments.map { it[downloadId].orEmpty() }

    override suspend fun insertSegments(segments: List<SegmentEntity>) {
        segments.groupBy { it.downloadId }.forEach { (downloadId, group) ->
            val existing = this.segments.value[downloadId].orEmpty()
                .associateBy { it.index } + group.associateBy { it.index }
            this.segments.value =
                this.segments.value + (downloadId to existing.values.sortedBy { it.index })
        }
    }

    override suspend fun updateSegmentProgress(downloadId: Long, index: Int, downloadedBytes: Long) {
        val updated = segments.value[downloadId].orEmpty().map {
            if (it.index == index) it.copy(downloadedBytes = downloadedBytes) else it
        }
        segments.value = segments.value + (downloadId to updated)
    }

    override suspend fun deleteSegments(downloadId: Long) {
        segments.value = segments.value - downloadId
    }
}
