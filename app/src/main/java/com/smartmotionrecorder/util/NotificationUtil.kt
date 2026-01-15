package com.smartmotionrecorder.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.smartmotionrecorder.R
import com.smartmotionrecorder.data.MonitoringStatus

object NotificationUtil {
    const val CHANNEL_ID = "monitor_channel"
    const val NOTIFICATION_ID = 1001
    const val REMOTE_NOTIFICATION_ID = 1002
    const val TAMPER_NOTIFICATION_ID = 1003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        status: MonitoringStatus,
        ratio: Float,
        lastFile: String?,
        stopIntent: PendingIntent
    ): Notification {
        val statusText = when (status) {
            MonitoringStatus.RECORDING -> "Recording (motion detected)"
            MonitoringStatus.MONITORING -> "Monitoring (waiting for motion)"
            MonitoringStatus.IDLE -> "Idle"
        }
        val contentText = buildString {
            append(statusText)
            append(" · ratio=")
            append(String.format("%.3f", ratio))
            lastFile?.let {
                append(" · last=")
                append(it.substringAfterLast('/'))
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(context, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(context)
        }

        return builder
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }

    fun buildRemoteNotification(
        context: Context,
        port: Int,
        stopIntent: PendingIntent
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(context, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(context)
        }
        return builder
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Remote kontrol aktif · port=$port")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.notification_stop),
                stopIntent
            )
            .build()
    }

    fun showTamperNotification(context: Context, message: String) {
        ensureChannel(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(context, CHANNEL_ID)
        } else {
            NotificationCompat.Builder(context)
        }
        val notification = builder
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
        manager?.notify(TAMPER_NOTIFICATION_ID, notification)
    }
}
