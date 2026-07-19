package com.genesiscruz.downloadmanager.ui.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.service.BrowserEntry
import com.genesiscruz.downloadmanager.service.FolderFile
import com.genesiscruz.downloadmanager.service.SystemDownload
import com.genesiscruz.downloadmanager.util.FileOpener
import com.genesiscruz.downloadmanager.util.Formatters
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val browserState by viewModel.browserState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.restoreBrowsingIfAny() }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::onFolderPicked) }

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
                4 -> if (browserState.isActive) {
                    FolderBrowserView(
                        state = browserState,
                        onUp = { viewModel.navigateUp() },
                        onBackToDownloads = { viewModel.closeBrowser() },
                        onPickDifferentFolder = { pickFolderLauncher.launch(null) },
                        onEntryClick = { entry ->
                            if (entry.isDirectory) {
                                viewModel.navigateInto(entry)
                            } else {
                                FileOpener.open(context, entry.uri, entry.mimeType, entry.name)
                            }
                        },
                        onDelete = { viewModel.deleteBrowserEntry(it) }
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                            OutlinedButton(onClick = { pickFolderLauncher.launch(null) }) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Text(" Browse other folder", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        FolderFileList(
                            files = state.folderFiles,
                            onOpen = { FileOpener.open(context, it.uri, it.mimeType, it.name) },
                            onDelete = { viewModel.deleteFolderFile(it) }
                        )
                    }
                }
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
private fun FolderBrowserView(
    state: BrowserUiState,
    onUp: () -> Boolean,
    onBackToDownloads: () -> Unit,
    onPickDifferentFolder: () -> Unit,
    onEntryClick: (BrowserEntry) -> Unit,
    onDelete: (BrowserEntry) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.canGoUp) {
                IconButton(onClick = { onUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                }
            }
            Text(
                state.breadcrumb,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onPickDifferentFolder) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Pick a different folder")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            Button(onClick = onBackToDownloads) { Text("Back to Downloads") }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.entries.isEmpty() -> EmptyState("This folder is empty.")
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.entries, key = { it.documentId }) { entry ->
                    BrowserEntryRow(
                        entry = entry,
                        onClick = { onEntryClick(entry) },
                        onDelete = { onDelete(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserEntryRow(
    entry: BrowserEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.isDirectory) Icons.Filled.Folder else iconFor(entry.name),
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isDirectory) {
                Text(
                    Formatters.bytes(entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!entry.isDirectory) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
    // Files arrive newest-first, so groupBy naturally yields date sections in
    // the same order: Today, Yesterday, then older dates descending.
    val groups = remember(files) { files.groupBy { dateBucketLabel(it.dateModifiedMillis) } }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        groups.forEach { (label, filesInGroup) ->
            item(key = "header_$label") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${filesInGroup.size} item${if (filesInGroup.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "group_$label") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column {
                        filesInGroup.forEachIndexed { index, file ->
                            FolderFileRow(
                                file = file,
                                timeLabel = timeFormat.format(Date(file.dateModifiedMillis)),
                                onOpen = { onOpen(file) },
                                onDelete = { onDelete(file) }
                            )
                            if (index != filesInGroup.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderFileRow(
    file: FolderFile,
    timeLabel: String,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            iconFor(file.name),
            contentDescription = null,
            modifier = Modifier.padding(end = 12.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${Formatters.bytes(file.sizeBytes)} · $timeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.Filled.OpenInNew, contentDescription = "Open")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

private fun dateBucketLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}

private fun iconFor(fileName: String): ImageVector = when (fileName.substringAfterLast('.', "").lowercase()) {
    "apk" -> Icons.Filled.Android
    "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Filled.Image
    "mp4", "mkv", "avi", "mov", "webm" -> Icons.Filled.VideoFile
    "pdf" -> Icons.Filled.PictureAsPdf
    "doc", "docx", "txt" -> Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
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
