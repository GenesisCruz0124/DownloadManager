package com.genesiscruz.downloadmanager.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.genesiscruz.downloadmanager.DownloadManagerApp
import com.genesiscruz.downloadmanager.MainActivity
import com.genesiscruz.downloadmanager.R
import com.genesiscruz.downloadmanager.data.db.DownloadEntity
import com.genesiscruz.downloadmanager.data.db.DownloadStatus
import com.genesiscruz.downloadmanager.data.repo.DownloadRepository
import com.genesiscruz.downloadmanager.engine.DownloadEngine
import com.genesiscruz.downloadmanager.receiver.NotificationActionReceiver
import com.genesiscruz.downloadmanager.util.Formatters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the process alive while downloads run and
 * mirrors their progress into notifications. Stops itself once nothing is
 * active anymore.
 */
@AndroidEntryPoint
class DownloadService : LifecycleService() {

    @Inject lateinit var repo: DownloadRepository
    @Inject lateinit var engine: DownloadEngine

    private val shownIds = mutableSetOf<Long>()

    override fun onCreate() {
        super.onCreate()
        startInForeground(activeCount = 0)
        lifecycleScope.launch {
            combine(repo.observeAll(), engine.liveProgress) { downloads, live ->
                downloads to live
            }.collect { (downloads, live) ->
                val active = downloads.filter { it.status.isActive() }
                updateNotifications(active, live)
                if (active.isEmpty()) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        lifecycleScope.launch { engine.recoverAfterRestart() }
        return START_STICKY
    }

    private fun startInForeground(activeCount: Int) {
        val notification = summaryNotification(activeCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SUMMARY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(SUMMARY_ID, notification)
        }
    }

    private fun updateNotifications(
        active: List<DownloadEntity>,
        live: Map<Long, com.genesiscruz.downloadmanager.engine.LiveProgress>
    ) {
        val manager = NotificationManagerCompat.from(this)
        startInForeground(active.size)

        val activeIds = active.map { it.id }.toSet()
        (shownIds - activeIds).forEach { manager.cancel(it.toInt()) }
        shownIds.retainAll(activeIds)

        if (!hasNotificationPermission()) return
        active.forEach { download ->
            shownIds += download.id
            manager.notify(download.id.toInt(), downloadNotification(download, live[download.id]))
        }
    }

    private fun summaryNotification(activeCount: Int): Notification =
        NotificationCompat.Builder(this, DownloadManagerApp.CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                resources.getQuantityString(
                    R.plurals.active_downloads_count, activeCount, activeCount
                )
            )
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun downloadNotification(
        download: DownloadEntity,
        live: com.genesiscruz.downloadmanager.engine.LiveProgress?
    ): Notification {
        val builder = NotificationCompat.Builder(this, DownloadManagerApp.CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(download.fileName)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        when (download.status) {
            DownloadStatus.RUNNING -> {
                val speed = live?.bytesPerSecond ?: 0
                builder.setContentText(
                    "${Formatters.bytes(download.downloadedBytes)} / " +
                        "${Formatters.bytes(download.totalBytes)} · ${Formatters.speed(speed)}"
                )
                if (download.totalBytes > 0) {
                    val percent =
                        (download.downloadedBytes * 100 / download.totalBytes).toInt()
                    builder.setProgress(100, percent, false)
                } else {
                    builder.setProgress(0, 0, true)
                }
                builder.addAction(
                    0, "Pause",
                    NotificationActionReceiver.pendingIntent(
                        this, NotificationActionReceiver.ACTION_PAUSE, download.id
                    )
                )
            }
            DownloadStatus.QUEUED, DownloadStatus.CONNECTING -> {
                builder.setContentText("Waiting…").setProgress(0, 0, true)
            }
            else -> builder.setContentText(download.status.name.lowercase())
        }
        builder.addAction(
            0, "Cancel",
            NotificationActionReceiver.pendingIntent(
                this, NotificationActionReceiver.ACTION_CANCEL, download.id
            )
        )
        return builder.build()
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        private const val SUMMARY_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DownloadService::class.java))
        }
    }
}

fun DownloadStatus.isActive(): Boolean = this in listOf(
    DownloadStatus.QUEUED, DownloadStatus.CONNECTING, DownloadStatus.RUNNING
)
