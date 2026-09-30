package com.mobileagent.models

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.mobileagent.MainActivity

/** Benachrichtigungen fuer Modell-Downloads im Hintergrund. */
class ModelDownloadNotifications(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Modell-Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Fortschritt und Abschluss von Modell-Downloads" },
        )
    }

    fun foregroundInfo(spec: ModelSpec, downloaded: Long, total: Long): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${spec.displayName} wird geladen")
            .setContentText(progressText(downloaded, total))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent(downloaded, total), total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .build()
        return ForegroundInfo(notificationIdFor(spec.id), notification)
    }

    fun showCompleted(spec: ModelSpec) {
        if (!canPost()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${spec.displayName} ist bereit")
            .setContentText("Verifiziert gespeichert und einsatzbereit.")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        manager()?.notify(notificationIdFor(spec.id), notification)
    }

    fun showFailed(spec: ModelSpec, message: String?) {
        if (!canPost()) return
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("${spec.displayName}: Download fehlgeschlagen")
            .setContentText(listOfNotNull(message, "Zum erneuten Versuchen tippen").joinToString(" – "))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        manager()?.notify(notificationIdFor(spec.id), notification)
    }

    fun cancel(spec: ModelSpec) {
        manager()?.cancel(notificationIdFor(spec.id))
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun contentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun manager(): NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL_ID = "model_downloads"
        private const val REQUEST_OPEN_APP = 2001

        fun notificationIdFor(modelId: String): Int =
            "model-download-$modelId".hashCode()

        fun progressPercent(downloaded: Long, total: Long): Int =
            if (total > 0) ((downloaded.coerceAtLeast(0) * 100) / total).toInt().coerceIn(0, 100)
            else 0

        fun megabytes(bytes: Long): Long = (bytes.coerceAtLeast(0)) / 1_048_576L

        fun progressText(downloaded: Long, total: Long): String =
            if (total > 0) {
                "${megabytes(downloaded)} / ${megabytes(total)} MB · ${progressPercent(downloaded, total)} %"
            } else {
                "${megabytes(downloaded)} MB geladen …"
            }
    }
}
