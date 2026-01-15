package com.smartmotionrecorder.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.smartmotionrecorder.camera.MotionCameraController
import com.smartmotionrecorder.data.MonitoringSettings
import com.smartmotionrecorder.data.MonitoringStatus
import com.smartmotionrecorder.data.ServiceStateRepository
import com.smartmotionrecorder.util.MonitoringStateStore
import com.smartmotionrecorder.util.NotificationUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class BackgroundMonitorService : LifecycleService() {
    private var controller: MotionCameraController? = null
    private var useBackCamera: Boolean = true
    private var recordAudio: Boolean = true
    private var settings: MonitoringSettings = MonitoringSettings()
    private var heartbeatJob: Job? = null
    private var stoppedByUser: Boolean = false

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                stoppedByUser = false
                useBackCamera = intent.getBooleanExtra(EXTRA_USE_BACK, true)
                recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, true)
                settings = ServiceConfig.settings
                startForegroundFlow()
            }
            ACTION_SWITCH_CAMERA -> {
                useBackCamera = intent.getBooleanExtra(EXTRA_USE_BACK, useBackCamera)
                recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, recordAudio)
                settings = ServiceConfig.settings
                startForegroundFlow()
            }
            ACTION_STOP -> {
                stoppedByUser = true
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundFlow() {
        NotificationUtil.ensureChannel(this)
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BackgroundMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            NotificationUtil.NOTIFICATION_ID,
            NotificationUtil.buildNotification(
                context = this,
                status = MonitoringStatus.MONITORING,
                ratio = 0f,
                lastFile = null,
                stopIntent = stopIntent
            )
        )
        MonitoringStateStore.markMonitoringStarted(this, "bg")
        startHeartbeat()
        if (controller == null) {
            controller = MotionCameraController(
                context = this,
                lifecycleOwner = this,
                callback = object : MotionCameraController.Callback {
                    override fun onMotion(ratio: Float, avg: Float) {
                        ServiceStateRepository.updateMotion(ratio, avg)
                        updateNotification(MonitoringStatus.MONITORING, avg, ServiceStateRepository.serviceState.value.lastSavedFile)
                    }

                    override fun onMotionTrigger() {
                        ServiceStateRepository.addLog("Motion detected (bg)")
                    }

                    override fun onRecordingStarted(path: String) {
                        ServiceStateRepository.setStatus(MonitoringStatus.RECORDING)
                        ServiceStateRepository.setLastSavedFile(path)
                        ServiceConfig.lastSavedFile = path
                        updateNotification(MonitoringStatus.RECORDING, ServiceStateRepository.serviceState.value.motionRatioAvg, path)
                        ServiceStateRepository.addLog("Recording started (bg): $path")
                    }

                    override fun onRecordingStopped(path: String?) {
                        ServiceStateRepository.setStatus(MonitoringStatus.MONITORING)
                        updateNotification(MonitoringStatus.MONITORING, ServiceStateRepository.serviceState.value.motionRatioAvg, path)
                        ServiceStateRepository.addLog("Recording stopped (bg)")
                    }

                    override fun onError(message: String) {
                        ServiceStateRepository.setMessage("Error: $message")
                        updateNotification(MonitoringStatus.MONITORING, ServiceStateRepository.serviceState.value.motionRatioAvg, ServiceStateRepository.serviceState.value.lastSavedFile)
                    }
                }
            )
        }
        ServiceStateRepository.setStatus(MonitoringStatus.MONITORING)
        lifecycleScope.launch {
            controller?.start(
                previewProvider = null, // bg mode tanpa preview
                useBackCamera = useBackCamera,
                recordAudio = recordAudio,
                settings = settings,
                monitoringEnabled = true
            )
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = lifecycleScope.launch {
            while (isActive) {
                MonitoringStateStore.heartbeat(this@BackgroundMonitorService)
                delay(30_000)
            }
        }
    }

    private fun updateNotification(status: MonitoringStatus, ratio: Float, lastFile: String?) {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, BackgroundMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationUtil.buildNotification(
            context = this,
            status = status,
            ratio = ratio,
            lastFile = lastFile,
            stopIntent = stopIntent
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationUtil.NOTIFICATION_ID, notif)
    }

    override fun onDestroy() {
        controller?.stop()
        heartbeatJob?.cancel()
        if (MonitoringStateStore.isExpected(this)) {
            if (stoppedByUser) {
                MonitoringStateStore.markMonitoringStopped(this, "manual", expectedStop = true)
            } else {
                MonitoringStateStore.markMonitoringStopped(this, "service_killed", expectedStop = false)
                if (settings.antiTamperEnabled) {
                    NotificationUtil.showTamperNotification(this, "Monitoring berhenti tiba-tiba")
                }
            }
        }
        ServiceStateRepository.setStatus(MonitoringStatus.IDLE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.smartmotionrecorder.action.START_BG"
        const val ACTION_SWITCH_CAMERA = "com.smartmotionrecorder.action.SWITCH_CAMERA"
        const val ACTION_STOP = "com.smartmotionrecorder.action.STOP_BG"
        const val EXTRA_USE_BACK = "extra_use_back"
        const val EXTRA_RECORD_AUDIO = "extra_record_audio"
    }
}
