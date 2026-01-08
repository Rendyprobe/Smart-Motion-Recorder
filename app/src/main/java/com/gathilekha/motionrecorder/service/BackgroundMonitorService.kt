package com.gathilekha.motionrecorder.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gathilekha.motionrecorder.camera.MotionCameraController
import com.gathilekha.motionrecorder.data.MonitoringSettings
import com.gathilekha.motionrecorder.data.MonitoringStatus
import com.gathilekha.motionrecorder.data.ServiceStateRepository
import com.gathilekha.motionrecorder.util.NotificationUtil
import kotlinx.coroutines.launch

class BackgroundMonitorService : LifecycleService() {
    private var controller: MotionCameraController? = null
    private var useBackCamera: Boolean = true
    private var recordAudio: Boolean = true
    private var settings: MonitoringSettings = MonitoringSettings()

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                useBackCamera = intent.getBooleanExtra(EXTRA_USE_BACK, true)
                recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, true)
                settings = ServiceConfig.settings
                startForegroundFlow()
            }
            ACTION_STOP -> stopSelf()
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
                settings = settings
            )
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
        ServiceStateRepository.setStatus(MonitoringStatus.IDLE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.gathilekha.motionrecorder.action.START_BG"
        const val ACTION_STOP = "com.gathilekha.motionrecorder.action.STOP_BG"
        const val EXTRA_USE_BACK = "extra_use_back"
        const val EXTRA_RECORD_AUDIO = "extra_record_audio"
    }
}
