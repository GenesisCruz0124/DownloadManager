package com.genesiscruz.downloadmanager.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genesiscruz.downloadmanager.data.settings.SettingsDataStore
import com.genesiscruz.downloadmanager.detect.UrlUtils
import com.genesiscruz.downloadmanager.engine.DownloadEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddDownloadUiState(
    val url: String = "",
    val fileName: String = "",
    val segments: Int? = null,
    val urlError: Boolean = false
)

@HiltViewModel
class AddDownloadViewModel @Inject constructor(
    private val engine: DownloadEngine,
    private val settings: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddDownloadUiState())
    val uiState: StateFlow<AddDownloadUiState> = _uiState

    fun prefill(url: String) {
        _uiState.value = AddDownloadUiState(
            url = url,
            fileName = UrlUtils.fileNameFromUrl(url).orEmpty()
        )
    }

    fun onUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(url = value, urlError = false)
        if (_uiState.value.fileName.isBlank()) {
            UrlUtils.fileNameFromUrl(value)?.let {
                _uiState.value = _uiState.value.copy(fileName = it)
            }
        }
    }

    fun onFileNameChange(value: String) {
        _uiState.value = _uiState.value.copy(fileName = value)
    }

    fun onSegmentsChange(value: Int?) {
        _uiState.value = _uiState.value.copy(segments = value)
    }

    /** @return true when the download was enqueued and the sheet can close. */
    fun submit(): Boolean {
        val state = _uiState.value
        if (!UrlUtils.isHttpUrl(state.url)) {
            _uiState.value = state.copy(urlError = true)
            return false
        }
        viewModelScope.launch {
            engine.enqueue(
                url = state.url,
                fileName = state.fileName.takeIf { it.isNotBlank() },
                segments = state.segments
            )
        }
        _uiState.value = AddDownloadUiState()
        return true
    }

    fun reset() {
        _uiState.value = AddDownloadUiState()
    }
}
