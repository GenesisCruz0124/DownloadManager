package com.genesiscruz.downloadmanager.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.service.FolderFile
import com.genesiscruz.downloadmanager.service.SystemDownload
import com.genesiscruz.downloadmanager.util.FileOpener
import com.genesiscruz.downloadmanager.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TABS = listOf("All", "Active", "Done", "System", "Folder")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    onDownloadClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DownloadListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add download")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (tab) {
                3 -> SystemDownloadList(state.systemDownloads)
                4 -> FolderFileList(
                    files = state.folderFiles,
                    onOpen = { FileOpener.open(context, it.uri, it.mimeType, it.name) },
                    onDelete = { viewModel.deleteFolderFile(it) }
                )
                else -> {
                    val downloads = when (tab) {
                        1 -> state.downloads.filter {
                            it.status in listOf(
                                DownloadStatus.QUEUED, DownloadStatus.CONNECTING,
                                DownloadStatus.RUNNING, DownloadStatus.PAUSED
                            )
                        }
                        2 -> state.downloads.filter { it.status == DownloadStatus.COMPLETED }
                        else -> state.downloads
                    }
                    if (downloads.isEmpty()) {
                        EmptyState("No downloads yet. Tap + to add one, or share a link to the app.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(downloads, key = { it.id }) { download ->
                                DownloadItemCard(
                                    download = download,
                                    live = state.liveProgress[download.id],
                                    onClick = { onDownloadClick(download.id) },
                                    onPause = { viewModel.pause(download.id) },
                                    onResume = { viewModel.resume(download.id) },
                                    onRetry = { viewModel.retry(download.id) },
                                    onCancel = { viewModel.cancel(download.id) },
                                    onOpen = { FileOpener.open(context, download) },
                                    onDelete = { viewModel.delete(download) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemDownloadList(downloads: List<SystemDownload>) {
    if (downloads.isEmpty()) {
        EmptyState(
            "No system downloads visible. Downloads started by other apps via " +
                "Android's download manager appear here (read-only)."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(downloads, key = { it.id }) { item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.statusLabel} · ${Formatters.bytes(item.downloadedBytes)}" +
                        if (item.totalBytes > 0) " / ${Formatters.bytes(item.totalBytes)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.totalBytes > 0 && item.downloadedBytes < item.totalBytes) {
                    LinearProgressIndicator(
                        progress = { item.downloadedBytes.toFloat() / item.totalBytes },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderFileList(
    files: List<FolderFile>,
    onOpen: (FolderFile) -> Unit,
    onDelete: (FolderFile) -> Unit
) {
    if (files.isEmpty()) {
        EmptyState("No files found in the device's Downloads folder.")
        return
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files, key = { it.key }) { file ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        file.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${Formatters.bytes(file.sizeBytes)} · " +
                            dateFormat.format(Date(file.dateModifiedMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onOpen(file) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open")
                }
                IconButton(onClick = { onDelete(file) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
