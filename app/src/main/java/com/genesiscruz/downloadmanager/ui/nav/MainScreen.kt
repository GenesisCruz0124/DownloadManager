package com.genesiscruz.downloadmanager.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController

/**
 * Hosts the nav graph plus the add-download sheet. When [incomingUrl] is set
 * (browser hand-off, share, clipboard), the sheet opens prefilled with it.
 */
@Composable
fun MainScreen(
    navController: NavHostController,
    incomingUrl: String?,
    onIncomingUrlConsumed: () -> Unit
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var prefillUrl by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            prefillUrl = incomingUrl
            showAddSheet = true
            onIncomingUrlConsumed()
        }
    }

    NavGraph(
        navController = navController,
        showAddSheet = showAddSheet,
        prefillUrl = prefillUrl,
        onAddSheetChange = { visible ->
            showAddSheet = visible
            if (!visible) prefillUrl = null
        }
    )
}
