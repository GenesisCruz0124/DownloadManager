package com.genesiscruz.downloadmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.service.DownloadService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Restarts the download service after reboot when unfinished downloads exist. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: DownloadRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (repo.resumableDownloads().isNotEmpty()) {
                    DownloadService.start(context)
                }
            } finally {
                result.finish()
            }
        }
    }
}
