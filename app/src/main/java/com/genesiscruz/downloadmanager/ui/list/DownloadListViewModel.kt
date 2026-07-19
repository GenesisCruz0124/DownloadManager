package com.genesiscruz.downloadmanager.ui.list

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.engine.DownloadEngine
import com.genesiscruz.downloadmanager.engine.LiveProgress
import com.genesiscruz.downloadmanager.service.BrowserEntry
import com.genesiscruz.downloadmanager.service.BrowserFolder
import com.genesiscruz.downloadmanager.service.DocumentBrowser
import com.genesiscruz.downloadmanager.service.DownloadsFolderObserver
import com.genesiscruz.downloadmanager.service.FolderFile
import com.genesiscruz.downloadmanager.service.SystemDownload
import com.genesiscruz.downloadmanager.service.SystemDownloadObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DownloadListUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val liveProgress: Map<Long, LiveProgress> = emptyMap(),
    val systemDownloads: List<SystemDownload> = emptyList(),
    val folderFiles: List<FolderFile> = emptyList()
)

/** Null [treeUri] means "not browsing an external folder" (showing Downloads). */
data class BrowserUiState(
    val treeUri: Uri? = null,
    val stack: List<BrowserFolder> = emptyList(),
    val entries: List<BrowserEntry> = emptyList(),
    val loading: Boolean = false
) {
    val isActive: Boolean get() = treeUri != null
    val canGoUp: Boolean get() = stack.size > 1
    val breadcrumb: String get() = stack.joinToString(" / ") { it.name }
}

@HiltViewModel
class DownloadListViewModel @Inject constructor(
    repo: DownloadRepository,
    private val engine: DownloadEngine,
    systemObserver: SystemDownloadObserver,
    private val folderObserver: DownloadsFolderObserver,
    private val documentBrowser: DocumentBrowser
) : ViewModel() {

    val uiState: StateFlow<DownloadListUiState> = combine(
        repo.observeAll(),
        engine.liveProgress,
        systemObserver.downloads,
        folderObserver.files
    ) { downloads, live, system, folder ->
        DownloadListUiState(downloads, live, system, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadListUiState())

    private val _browser = MutableStateFlow(BrowserUiState())
    val browserState: StateFlow<BrowserUiState> = _browser

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

    /** Resumes browsing the last folder the user picked, if any. */
    fun restoreBrowsingIfAny() {
        if (_browser.value.isActive) return
        documentBrowser.savedTreeUri()?.let { openRoot(it) }
    }

    fun onFolderPicked(uri: Uri) {
        documentBrowser.rememberTreeUri(uri)
        openRoot(uri)
    }

    private fun openRoot(uri: Uri) {
        _browser.value = BrowserUiState(treeUri = uri, stack = listOf(documentBrowser.rootFolder(uri)))
        loadCurrentFolder()
    }

    fun navigateInto(entry: BrowserEntry) {
        if (!entry.isDirectory) return
        val state = _browser.value
        _browser.value = state.copy(stack = state.stack + BrowserFolder(entry.documentId, entry.name))
        loadCurrentFolder()
    }

    /** @return true if it moved up a level, false if already at the root. */
    fun navigateUp(): Boolean {
        val state = _browser.value
        if (!state.canGoUp) return false
        _browser.value = state.copy(stack = state.stack.dropLast(1))
        loadCurrentFolder()
        return true
    }

    /** Leaves browsing mode and returns to the Downloads view (folder stays remembered). */
    fun closeBrowser() {
        _browser.value = BrowserUiState()
    }

    fun deleteBrowserEntry(entry: BrowserEntry) = viewModelScope.launch {
        withContext(Dispatchers.IO) { documentBrowser.delete(entry) }
        loadCurrentFolder()
    }

    private fun loadCurrentFolder() = viewModelScope.launch {
        val state = _browser.value
        val treeUri = state.treeUri ?: return@launch
        val folder = state.stack.lastOrNull() ?: return@launch
        _browser.value = state.copy(loading = true)
        val entries = withContext(Dispatchers.IO) { documentBrowser.list(treeUri, folder.documentId) }
        if (_browser.value.treeUri == treeUri) {
            _browser.value = _browser.value.copy(entries = entries, loading = false)
        }
    }
}
