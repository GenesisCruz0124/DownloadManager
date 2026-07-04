package com.genesiscruz.downloadmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genesiscruz.downloadmanager.data.settings.AppSettings
import com.genesiscruz.downloadmanager.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<AppSettings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setMaxConcurrent(value: Int) = viewModelScope.launch {
        settings.setMaxConcurrentDownloads(value)
    }

    fun setSegments(value: Int) = viewModelScope.launch {
        settings.setSegmentsPerDownload(value)
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch {
        settings.setWifiOnly(value)
    }

    fun setClipboardDetection(value: Boolean) = viewModelScope.launch {
        settings.setClipboardDetection(value)
    }
}
