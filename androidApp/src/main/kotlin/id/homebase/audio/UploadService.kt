package id.homebase.audio

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import co.touchlab.kermit.Logger
import id.homebase.audio.importing.ImportJob
import id.homebase.audio.importing.ImportStatus
import id.homebase.audio.importing.TrackImporter
import id.homebase.audio.importing.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the process running while imports are queued or uploading. Without it Android freezes the
 * app shortly after it leaves the screen and the upload's connection dies.
 */
class UploadService : Service() {
    private val importer: TrackImporter by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var wifiLock: WifiManager.WifiLock
    private var started = false

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SimplyAudio:upload")
            .apply { setReferenceCounted(false) }
        @Suppress("DEPRECATION")
        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SimplyAudio:upload")
            .apply { setReferenceCounted(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.upload_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService requires startForeground within seconds, even if the queue just emptied.
        startForeground(NOTIFICATION_ID, notification(importer.jobs.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!started) {
            started = true
            scope.launch { importer.jobs.collect(::render) }
        }
        return START_NOT_STICKY
    }

    // Android 15 caps dataSync services at 6 hours a day; the import queue is on disk and resumes on next launch.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
        super.onDestroy()
    }

    private fun render(jobs: List<ImportJob>) {
        if (jobs.none { it.isActive }) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (!wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        if (!wifiLock.isHeld) wifiLock.acquire()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(jobs))
    }

    private fun notification(jobs: List<ImportJob>): Notification {
        val current = jobs.firstOrNull { it.status == ImportStatus.Uploading || it.status == ImportStatus.Retrying }
        val batch = jobs.filter { it.status != ImportStatus.Failed }
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.upload_notification_title))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        when {
            batch.size > 1 -> {
                val done = batch.count { it.status == ImportStatus.Done }
                val progress = (done + batch.filter { it.status == ImportStatus.Uploading }.sumOf { it.progress.toDouble() }) / batch.size
                builder
                    .setContentText(getString(R.string.upload_notification_count, done, batch.size))
                    .setProgress(PROGRESS_MAX, (progress * PROGRESS_MAX).toInt(), false)
            }
            current?.status == ImportStatus.Uploading -> builder
                .setContentText(current.fileName)
                .setProgress(PROGRESS_MAX, (current.progress * PROGRESS_MAX).toInt(), current.progress <= 0f)
            current?.status == ImportStatus.Retrying -> builder
                .setContentText(getString(R.string.upload_notification_retrying))
            else -> builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "uploads"
        private const val NOTIFICATION_ID = 2
        private const val PROGRESS_MAX = 1000
        // Safety net only; released as soon as the queue empties.
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, UploadService::class.java))
            } catch (e: ForegroundServiceStartNotAllowedException) {
                // Queue resumed while the app is in the background; MainActivity starts the service when it's opened.
                Logger.w(e, "UploadService") { "Not allowed to start the upload service from the background" }
            }
        }
    }
}
