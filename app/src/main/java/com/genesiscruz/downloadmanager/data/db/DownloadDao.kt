package com.genesiscruz.downloadmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun getByStatus(statuses: List<DownloadStatus>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getByStatusOrdered(status: DownloadStatus, limit: Int): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Update
    suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DownloadStatus, error: String? = null)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedBytes: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    // --- Segments ---

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY `index`")
    suspend fun getSegments(downloadId: Long): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE downloadId = :downloadId ORDER BY `index`")
    fun observeSegments(downloadId: Long): Flow<List<SegmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Query("UPDATE segments SET downloadedBytes = :downloadedBytes WHERE downloadId = :downloadId AND `index` = :index")
    suspend fun updateSegmentProgress(downloadId: Long, index: Int, downloadedBytes: Long)

    @Query("DELETE FROM segments WHERE downloadId = :downloadId")
    suspend fun deleteSegments(downloadId: Long)

    @Transaction
    suspend fun resetSegments(downloadId: Long, segments: List<SegmentEntity>) {
        deleteSegments(downloadId)
        insertSegments(segments)
    }
}
