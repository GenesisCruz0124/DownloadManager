package com.genesiscruz.downloadmanager.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.genesiscruz.downloadmanager.engine.DownloadEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var engine: DownloadEngine

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
        if (id < 0) return
        val action = intent.action ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_PAUSE -> engine.pause(id)
                    ACTION_RESUME -> engine.resume(id)
                    ACTION_CANCEL -> engine.cancel(id)
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.genesiscruz.downloadmanager.action.PAUSE"
        const val ACTION_RESUME = "com.genesiscruz.downloadmanager.action.RESUME"
        const val ACTION_CANCEL = "com.genesiscruz.downloadmanager.action.CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun pendingIntent(context: Context, action: String, downloadId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                (action.hashCode() * 31 + downloadId.toInt()),
                Intent(context, NotificationActionReceiver::class.java)
                    .setAction(action)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
