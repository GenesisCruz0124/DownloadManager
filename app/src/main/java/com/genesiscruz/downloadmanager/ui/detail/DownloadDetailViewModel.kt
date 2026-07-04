package com.genesiscruz.downloadmanager.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.SegmentEntity
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.engine.DownloadEngine
import com.genesiscruz.downloadmanager.engine.LiveProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadDetailUiState(
    val download: DownloadEntity? = null,
    val segments: List<SegmentEntity> = emptyList(),
    val live: LiveProgress? = null
)

@HiltViewModel
class DownloadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repo: DownloadRepository,
    private val engine: DownloadEngine
) : ViewModel() {

    private val downloadId: Long = checkNotNull(savedStateHandle["downloadId"])

    val uiState: StateFlow<DownloadDetailUiState> = combine(
        repo.observeById(downloadId),
        repo.observeSegments(downloadId),
        engine.liveProgress
    ) { download, segments, live ->
        DownloadDetailUiState(download, segments, live[downloadId])
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadDetailUiState())

    fun pause() = viewModelScope.launch { engine.pause(downloadId) }

    fun resume() = viewModelScope.launch { engine.resume(downloadId) }

    fun cancel() = viewModelScope.launch { engine.cancel(downloadId) }
}
