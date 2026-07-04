package com.genesiscruz.downloadmanager.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val maxConcurrentDownloads: Int = 3,
    val segmentsPerDownload: Int = 8,
    val wifiOnly: Boolean = false,
    val clipboardDetection: Boolean = true
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val SEGMENTS = intPreferencesKey("segments_per_download")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CLIPBOARD = booleanPreferencesKey("clipboard_detection")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT] ?: 3,
            segmentsPerDownload = prefs[Keys.SEGMENTS] ?: 8,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            clipboardDetection = prefs[Keys.CLIPBOARD] ?: true
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setMaxConcurrentDownloads(value: Int) {
        context.dataStore.edit { it[Keys.MAX_CONCURRENT] = value.coerceIn(1, 10) }
    }

    suspend fun setSegmentsPerDownload(value: Int) {
        context.dataStore.edit { it[Keys.SEGMENTS] = value.coerceIn(1, 16) }
    }

    suspend fun setWifiOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = value }
    }

    suspend fun setClipboardDetection(value: Boolean) {
        context.dataStore.edit { it[Keys.CLIPBOARD] = value }
    }
}
