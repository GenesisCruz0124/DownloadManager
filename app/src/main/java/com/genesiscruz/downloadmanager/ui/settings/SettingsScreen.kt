package com.genesiscruz.downloadmanager.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SliderSetting(
                title = "Parallel downloads",
                subtitle = "How many files download at the same time",
                value = settings.maxConcurrentDownloads,
                range = 1f..10f,
                onChange = viewModel::setMaxConcurrent
            )
            SliderSetting(
                title = "Connections per download",
                subtitle = "More connections can speed up large files on fast servers",
                value = settings.segmentsPerDownload,
                range = 1f..16f,
                onChange = viewModel::setSegments
            )
            SwitchSetting(
                title = "Wi-Fi only",
                subtitle = "Pause downloads on metered connections",
                checked = settings.wifiOnly,
                onChange = viewModel::setWifiOnly
            )
            SwitchSetting(
                title = "Clipboard detection",
                subtitle = "Offer to download links copied to the clipboard",
                checked = settings.clipboardDetection,
                onChange = viewModel::setClipboardDetection
            )
            HorizontalDivider()
            DefaultAppSetting(context)
        }
    }
}

@Composable
private fun DefaultAppSetting(context: Context) {
    Column {
        Text("Default download handler", style = MaterialTheme.typography.titleSmall)
        Text(
            "Android has no single \"default download manager\" switch. To make " +
                "download links open here automatically instead of asking every time, " +
                "turn on \"Open supported links\" (or manually add the sites you " +
                "download from) in the system link settings for this app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openLinkSettings(context) }) {
                Text("Open link settings")
            }
            OutlinedButton(onClick = { openAppInfo(context) }) {
                Text("App info")
            }
        }
    }
}

/** Jumps straight to this app's "Open by default" link-handling screen. */
private fun openLinkSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(
            AndroidSettings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    } else {
        // Pre-Android 12 has no direct deep link; App Info's "Open by default"
        // entry is the closest equivalent.
        Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    }
    runCatching { context.startActivity(intent) }
}

private fun openAppInfo(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    runCatching { context.startActivity(intent) }
}

@Composable
private fun SliderSetting(
    title: String,
    subtitle: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit
) {
    Column {
        Text("$title: $value", style = MaterialTheme.typography.titleSmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
