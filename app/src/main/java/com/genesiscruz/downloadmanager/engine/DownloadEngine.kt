package com.genesiscruz.downloadmanager.engine

import android.content.Context
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.data.settings.SettingsDataStore
import com.genesiscruz.downloadmanager.detect.UrlUtils
import com.genesiscruz.downloadmanager.service.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Live speed/ETA for one running download; not persisted. */
data class LiveProgress(val bytesPerSecond: Long, val etaSeconds: Long)

/**
 * Owns the lifecycle of all downloads: enqueueing, the max-concurrency gate,
 * pause/resume/cancel/retry, Wi-Fi-only enforcement, and finalizing completed
 * files into the public Downloads collection.
 */
@Singleton
class DownloadEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: DownloadRepository,
    private val client: OkHttpClient,
    private val settings: SettingsDataStore,
    private val networkMonitor: NetworkMonitor,
    private val fileFinalizer: FileFinalizer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val jobs = mutableMapOf<Long, Job>()
    private val tasks = mutableMapOf<Long, DownloadTask>()

    private val _liveProgress = MutableStateFlow<Map<Long, LiveProgress>>(emptyMap())
    val liveProgress: StateFlow<Map<Long, LiveProgress>> = _liveProgress

    /** Set when downloads are auto-paused because Wi-Fi-only is on and we're on metered data. */
    private val _pausedByNetwork = MutableStateFlow(false)
    val pausedByNetwork: StateFlow<Boolean> = _pausedByNetwork

    init {
        scope.launch {
            networkMonitor.state.collect { state ->
                val wifiOnly = settings.current().wifiOnly
                if (wifiOnly && (!state.connected || !state.unmetered)) {
                    _pausedByNetwork.value = true
                    pauseAllRunning()
                } else if (_pausedByNetwork.value && state.connected) {
                    _pausedByNetwork.value = false
                    resumeQueued()
                }
            }
        }
    }

    /** Creates a new download and starts it if a slot is free. */
    suspend fun enqueue(url: String, fileName: String? = null, segments: Int? = null): Long {
        val name = fileName?.takeIf { it.isNotBlank() }?.let { UrlUtils.sanitize(it) }
            ?: UrlUtils.fileNameFromUrl(url)
            ?: UrlUtils.FALLBACK_NAME
        val tempDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val entity = DownloadEntity(
            url = url.trim(),
            fileName = name,
            status = DownloadStatus.QUEUED,
            segmentCount = segments ?: settings.current().segmentsPerDownload,
            tempFilePath = "" // set below once we know the id
        )
        val id = repo.insert(entity)
        repo.update(
            entity.copy(id = id, tempFilePath = File(tempDir, "$id.part").absolutePath)
        )
        startNextIfPossible()
        DownloadService.start(context)
        return id
    }

    suspend fun pause(id: Long) {
        cancelJob(id)
        repo.updateStatus(id, DownloadStatus.PAUSED)
        startNextIfPossible()
    }

    suspend fun resume(id: Long) {
        val download = repo.getById(id) ?: return
        if (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.FAILED) {
            repo.updateStatus(id, DownloadStatus.QUEUED)
            startNextIfPossible()
            DownloadService.start(context)
        }
    }

    suspend fun cancel(id: Long) {
        cancelJob(id)
        repo.getById(id)?.let { download ->
            runCatching { File(download.tempFilePath).delete() }
            repo.updateStatus(id, DownloadStatus.CANCELLED)
        }
        startNextIfPossible()
    }

    suspend fun retry(id: Long) = resume(id)

    suspend fun delete(id: Long, deleteFile: Boolean = true) {
        cancelJob(id)
        repo.getById(id)?.let { repo.delete(it, deleteFile) }
        startNextIfPossible()
    }

    /** Re-enqueues RUNNING/CONNECTING rows left over from a previous process. */
    suspend fun recoverAfterRestart() {
        repo.resumableDownloads().forEach { download ->
            mutex.withLock {
                if (!jobs.containsKey(download.id)) {
                    repo.updateStatus(download.id, DownloadStatus.QUEUED)
                }
            }
        }
        startNextIfPossible()
    }

    fun hasActiveJobs(): Boolean = jobs.isNotEmpty()

    private suspend fun pauseAllRunning() {
        val ids = mutex.withLock { jobs.keys.toList() }
        ids.forEach { id ->
            cancelJob(id)
            repo.updateStatus(id, DownloadStatus.QUEUED)
        }
    }

    private suspend fun resumeQueued() = startNextIfPossible()

    private suspend fun cancelJob(id: Long) {
        val job = mutex.withLock { jobs.remove(id) }
        mutex.withLock { tasks.remove(id) }
        _liveProgress.update { it - id }
        job?.let {
            it.cancel()
            it.join()
        }
    }

    private suspend fun startNextIfPossible() {
        val appSettings = settings.current()
        if (appSettings.wifiOnly) {
            val network = networkMonitor.currentState()
            if (!network.connected || !network.unmetered) {
                _pausedByNetwork.value = true
                return
            }
        }
        mutex.withLock {
            val free = appSettings.maxConcurrentDownloads - jobs.size
            if (free <= 0) return
            val queued = repo.getByStatusOrdered(DownloadStatus.QUEUED, free)
            queued.forEach { download -> startLocked(download, appSettings.segmentsPerDownload) }
        }
    }

    private fun startLocked(download: DownloadEntity, defaultSegments: Int) {
        if (jobs.containsKey(download.id)) return
        val task = DownloadTask(client, repo)
        tasks[download.id] = task
        val job = scope.launch {
            try {
                repo.updateStatus(download.id, DownloadStatus.CONNECTING)
                val fresh = repo.getById(download.id) ?: return@launch
                repo.updateStatus(download.id, DownloadStatus.RUNNING)
                val progressJob = launch { publishLiveProgress(download.id, task) }
                val completed = try {
                    task.run(fresh, fresh.segmentCount.takeIf { it > 0 } ?: defaultSegments)
                } finally {
                    progressJob.cancel()
                }
                val finalUri = fileFinalizer.publish(completed)
                repo.update(
                    completed.copy(
                        status = DownloadStatus.COMPLETED,
                        finalUri = finalUri,
                        completedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repo.updateStatus(download.id, DownloadStatus.FAILED, e.message ?: "Download failed")
            } finally {
                mutex.withLock {
                    jobs.remove(download.id)
                    tasks.remove(download.id)
                }
                _liveProgress.update { it - download.id }
                scope.launch { startNextIfPossible() }
            }
        }
        jobs[download.id] = job
    }

    private suspend fun publishLiveProgress(id: Long, task: DownloadTask) {
        while (true) {
            kotlinx.coroutines.delay(500)
            val download = repo.getById(id) ?: break
            _liveProgress.update {
                it + (id to LiveProgress(
                    bytesPerSecond = task.speed.bytesPerSecond,
                    etaSeconds = task.speed.etaSeconds(download.downloadedBytes, download.totalBytes)
                ))
            }
        }
    }
}
