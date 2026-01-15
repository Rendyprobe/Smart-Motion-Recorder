package com.smartmotionrecorder.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TamperWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!MonitoringStateStore.isAntiTamperEnabled(applicationContext)) {
            return Result.success()
        }
        if (!MonitoringStateStore.isExpected(applicationContext)) {
            return Result.success()
        }
        val lastHeartbeat = MonitoringStateStore.getLastHeartbeat(applicationContext)
        val now = System.currentTimeMillis()
        val staleMs = 2 * 60 * 1000L
        if (lastHeartbeat == 0L || now - lastHeartbeat > staleMs) {
            val message = "Monitoring berhenti tiba-tiba"
            MonitoringStateStore.setTamperMessage(applicationContext, message)
            MonitoringStateStore.markMonitoringStopped(applicationContext, "watchdog", expectedStop = false)
            NotificationUtil.showTamperNotification(applicationContext, message)
        }
        return Result.success()
    }
}
