package com.genesiscruz.downloadmanager.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.genesiscruz.downloadmanager.ui.add.AddDownloadSheet
import com.genesiscruz.downloadmanager.ui.detail.DownloadDetailScreen
import com.genesiscruz.downloadmanager.ui.list.DownloadListScreen
import com.genesiscruz.downloadmanager.ui.settings.SettingsScreen

object Routes {
    const val LIST = "list"
    const val DETAIL = "detail/{downloadId}"
    const val SETTINGS = "settings"

    fun detail(id: Long) = "detail/$id"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    showAddSheet: Boolean,
    prefillUrl: String?,
    onAddSheetChange: (Boolean) -> Unit
) {
    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            DownloadListScreen(
                onDownloadClick = { navController.navigate(Routes.detail(it)) },
                onAddClick = { onAddSheetChange(true) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("downloadId") { type = NavType.LongType })
        ) {
            DownloadDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
    if (showAddSheet) {
        AddDownloadSheet(
            prefillUrl = prefillUrl,
            onDismiss = { onAddSheetChange(false) }
        )
    }
}
