package com.genesiscruz.downloadmanager

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.genesiscruz.downloadmanager.data.settings.SettingsDataStore
import com.genesiscruz.downloadmanager.detect.ClipboardMonitor
import com.genesiscruz.downloadmanager.detect.UrlUtils
import com.genesiscruz.downloadmanager.ui.nav.MainScreen
import com.genesiscruz.downloadmanager.ui.theme.DownloadManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var clipboardMonitor: ClipboardMonitor
    @Inject lateinit var settings: SettingsDataStore

    /** URL handed to us by a browser, share sheet, or the clipboard. */
    private var incomingUrl by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleIntent(intent)
        observeClipboard()

        setContent {
            DownloadManagerTheme {
                val navController = rememberNavController()
                MainScreen(
                    navController = navController,
                    incomingUrl = incomingUrl,
                    onIncomingUrlConsumed = { incomingUrl = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString
                if (url != null && UrlUtils.isHttpUrl(url)) incomingUrl = url
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = text?.let { UrlUtils.extractUrl(it) }
                if (url != null) incomingUrl = url
            }
        }
    }

    /**
     * Clipboard can only be read while the app is focused; check every time we
     * return to the foreground.
     */
    private fun observeClipboard() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (!settings.settings.first().clipboardDetection) return@repeatOnLifecycle
                // Clipboard content is not available until the window gains
                // focus, which happens just after RESUMED.
                window.decorView.post {
                    if (incomingUrl == null) {
                        clipboardMonitor.detectNewUrl()?.let { incomingUrl = it }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
