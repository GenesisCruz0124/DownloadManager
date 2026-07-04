package com.genesiscruz.downloadmanager.data.repo

import com.genesiscruz.downloadmanager.data.db.DownloadDao
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao
) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<DownloadEntity?> = dao.observeById(id)

    fun observeSegments(downloadId: Long): Flow<List<SegmentEntity>> =
        dao.observeSegments(downloadId)

    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun getSegments(downloadId: Long): List<SegmentEntity> = dao.getSegments(downloadId)

    suspend fun insert(download: DownloadEntity): Long = dao.insert(download)

    suspend fun update(download: DownloadEntity) = dao.update(download)

    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String? = null) =
        dao.updateStatus(id, status, error)

    suspend fun updateProgress(id: Long, downloadedBytes: Long) =
        dao.updateProgress(id, downloadedBytes)

    suspend fun updateSegmentProgress(downloadId: Long, index: Int, downloadedBytes: Long) =
        dao.updateSegmentProgress(downloadId, index, downloadedBytes)

    suspend fun resetSegments(downloadId: Long, segments: List<SegmentEntity>) =
        dao.resetSegments(downloadId, segments)

    suspend fun getByStatusOrdered(status: DownloadStatus, limit: Int): List<DownloadEntity> =
        dao.getByStatusOrdered(status, limit)

    suspend fun activeDownloads(): List<DownloadEntity> = dao.getByStatus(
        listOf(DownloadStatus.QUEUED, DownloadStatus.CONNECTING, DownloadStatus.RUNNING)
    )

    /** Downloads that should resume after process death or reboot. */
    suspend fun resumableDownloads(): List<DownloadEntity> = dao.getByStatus(
        listOf(DownloadStatus.QUEUED, DownloadStatus.CONNECTING, DownloadStatus.RUNNING)
    )

    suspend fun delete(download: DownloadEntity, deleteFile: Boolean) {
        if (deleteFile) {
            runCatching { File(download.tempFilePath).delete() }
        }
        dao.delete(download.id)
    }
}
