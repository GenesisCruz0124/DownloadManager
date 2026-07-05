package com.genesiscruz.downloadmanager.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.engine.LiveProgress
import com.genesiscruz.downloadmanager.util.Formatters

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    live: LiveProgress?,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        download.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        statusLine(download, live),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (download.status) {
                    DownloadStatus.RUNNING, DownloadStatus.CONNECTING, DownloadStatus.QUEUED -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Filled.Pause, contentDescription = "Pause")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Retry")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onOpen) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                    DownloadStatus.CANCELLED -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
            if (download.status in listOf(
                    DownloadStatus.RUNNING, DownloadStatus.PAUSED,
                    DownloadStatus.CONNECTING, DownloadStatus.QUEUED
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (download.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = {
                                download.downloadedBytes.toFloat() / download.totalBytes
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${Formatters.percent(download.downloadedBytes, download.totalBytes)}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun statusLine(download: DownloadEntity, live: LiveProgress?): String =
    when (download.status) {
        DownloadStatus.RUNNING -> buildString {
            append(Formatters.bytes(download.downloadedBytes))
            if (download.totalBytes > 0) append(" / ${Formatters.bytes(download.totalBytes)}")
            append(" · ${Formatters.speed(live?.bytesPerSecond ?: 0)}")
            if (live != null && live.etaSeconds >= 0) append(" · ${Formatters.eta(live.etaSeconds)} left")
        }
        DownloadStatus.PAUSED ->
            "Paused · ${Formatters.bytes(download.downloadedBytes)}" +
                if (download.totalBytes > 0) " / ${Formatters.bytes(download.totalBytes)}" else ""
        DownloadStatus.QUEUED -> "Queued"
        DownloadStatus.CONNECTING -> "Connecting…"
        DownloadStatus.COMPLETED -> "Completed · ${Formatters.bytes(download.totalBytes)}"
        DownloadStatus.FAILED -> "Failed · ${download.errorMessage ?: "unknown error"}"
        DownloadStatus.CANCELLED -> "Cancelled"
    }
