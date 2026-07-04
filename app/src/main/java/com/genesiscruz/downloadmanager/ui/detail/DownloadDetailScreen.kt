package com.genesiscruz.downloadmanager.ui.detail

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailScreen(
    onBack: () -> Unit,
    viewModel: DownloadDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val download = state.download

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(download?.fileName ?: "Download") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (download == null) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoRow("Status", download.status.name)
            InfoRow("URL", download.url)
            InfoRow(
                "Size",
                "${Formatters.bytes(download.downloadedBytes)} / ${Formatters.bytes(download.totalBytes)}"
            )
            state.live?.let { live ->
                InfoRow("Speed", Formatters.speed(live.bytesPerSecond))
                InfoRow("ETA", Formatters.eta(live.etaSeconds))
            }
            InfoRow("Connections", download.segmentCount.toString())
            InfoRow(
                "Range support",
                if (download.supportsRanges) "Yes (resumable)" else "No (single connection)"
            )
            download.errorMessage?.let { InfoRow("Error", it) }

            if (state.segments.size > 1) {
                HorizontalDivider()
                Text("Segments", style = MaterialTheme.typography.titleMedium)
                state.segments.forEach { segment ->
                    val size = segment.end - segment.start + 1
                    Column {
                        Text(
                            "#${segment.index + 1} · " +
                                "${Formatters.bytes(segment.downloadedBytes)} / ${Formatters.bytes(size)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        LinearProgressIndicator(
                            progress = {
                                (segment.downloadedBytes.toFloat() / size).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (download.status) {
                    DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.CONNECTING ->
                        Button(onClick = viewModel::pause) { Text("Pause") }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED ->
                        Button(onClick = viewModel::resume) { Text("Resume") }
                    DownloadStatus.COMPLETED -> {
                        download.finalUri?.let { uri ->
                            Button(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        uri.toUri(),
                                        download.mimeType ?: "*/*"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            }) { Text("Open") }
                        }
                    }
                    else -> {}
                }
                if (download.status !in listOf(
                        DownloadStatus.COMPLETED, DownloadStatus.CANCELLED
                    )
                ) {
                    OutlinedButton(onClick = {
                        viewModel.cancel()
                        onBack()
                    }) { Text("Cancel download") }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
