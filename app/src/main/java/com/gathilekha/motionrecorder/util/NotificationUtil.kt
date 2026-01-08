package com.gathilekha.motionrecorder.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.gathilekha.motionrecorder.R
import com.gathilekha.motionrecorder.data.MonitoringStatus

object NotificationUtil {
    const val CHANNEL_ID = "monitor_channel"
    const val NOTIFICATION_ID = 1001

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
}
