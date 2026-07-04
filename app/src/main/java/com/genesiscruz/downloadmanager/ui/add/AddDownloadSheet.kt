package com.genesiscruz.downloadmanager.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    onDismiss: () -> Unit,
    prefillUrl: String? = null,
    viewModel: AddDownloadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(prefillUrl) {
        if (prefillUrl != null) viewModel.prefill(prefillUrl)
    }

    ModalBottomSheet(onDismissRequest = {
        viewModel.reset()
        onDismiss()
    }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New download", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("URL") },
                isError = state.urlError,
                supportingText = if (state.urlError) {
                    { Text("Enter a valid http(s) URL") }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.fileName,
                onValueChange = viewModel::onFileNameChange,
                label = { Text("File name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                Text(
                    "Connections: ${state.segments ?: "default"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = (state.segments ?: 8).toFloat(),
                    onValueChange = { viewModel.onSegmentsChange(it.toInt()) },
                    valueRange = 1f..16f,
                    steps = 14
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = {
                    if (viewModel.submit()) onDismiss()
                }) {
                    Text("Download")
                }
            }
        }
    }
}
