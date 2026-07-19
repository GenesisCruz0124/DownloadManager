package com.genesiscruz.downloadmanager.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.engine.DownloadEngine
import com.genesiscruz.downloadmanager.engine.LiveProgress
import com.genesiscruz.downloadmanager.service.DownloadsFolderObserver
import com.genesiscruz.downloadmanager.service.FolderFile
import com.genesiscruz.downloadmanager.service.SystemDownload
import com.genesiscruz.downloadmanager.service.SystemDownloadObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadListUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val liveProgress: Map<Long, LiveProgress> = emptyMap(),
    val systemDownloads: List<SystemDownload> = emptyList(),
    val folderFiles: List<FolderFile> = emptyList()
)

@HiltViewModel
class DownloadListViewModel @Inject constructor(
    repo: DownloadRepository,
    private val engine: DownloadEngine,
    systemObserver: SystemDownloadObserver,
    private val folderObserver: DownloadsFolderObserver
) : ViewModel() {

    val uiState: StateFlow<DownloadListUiState> = combine(
        repo.observeAll(),
        engine.liveProgress,
        systemObserver.downloads,
        folderObserver.files
    ) { downloads, live, system, folder ->
        DownloadListUiState(downloads, live, system, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadListUiState())

    fun pause(id: Long) = viewModelScope.launch { engine.pause(id) }

    fun resume(id: Long) = viewModelScope.launch { engine.resume(id) }

    fun cancel(id: Long) = viewModelScope.launch { engine.cancel(id) }

    fun retry(id: Long) = viewModelScope.launch { engine.retry(id) }

    fun delete(download: DownloadEntity) = viewModelScope.launch {
        engine.delete(download.id, deleteFile = download.status != DownloadStatus.COMPLETED)
    }

    fun deleteFolderFile(file: FolderFile) = viewModelScope.launch {
        folderObserver.delete(file)
    }
}
