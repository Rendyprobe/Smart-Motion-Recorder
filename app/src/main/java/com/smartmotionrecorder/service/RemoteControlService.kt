package com.smartmotionrecorder.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.smartmotionrecorder.coin.CoinRepository
import com.smartmotionrecorder.camera.SnapshotCameraController
import com.smartmotionrecorder.data.SettingsDataStore
import com.smartmotionrecorder.remote.LocalRemoteServer
import com.smartmotionrecorder.util.MonitoringStateStore
import com.smartmotionrecorder.util.NotificationUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class RemoteControlService : LifecycleService() {
    private var server: LocalRemoteServer? = null
    private var snapshotController: SnapshotCameraController? = null
    private var snapshotJob: Job? = null
    private var port: Int = 8080
    private var pin: String = ""
    private val settingsStore by lazy { SettingsDataStore(applicationContext) }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                port = intent.getIntExtra(EXTRA_PORT, 8080).coerceIn(1024, 65535)
                pin = intent.getStringExtra(EXTRA_PIN) ?: ""
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
            Intent(this, RemoteControlService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startForeground(
            NotificationUtil.REMOTE_NOTIFICATION_ID,
            NotificationUtil.buildRemoteNotification(this, port, stopIntent)
        )
        startSnapshotMonitor()
        if (server == null) {
            server = LocalRemoteServer(object : LocalRemoteServer.Handler {
                override fun onStatus(): LocalRemoteServer.RemoteStatus {
                    val repo = CoinRepository.get(applicationContext)
                    val monitoring = MonitoringStateStore.isExpected(applicationContext)
                    val mode = if (monitoring) MonitoringStateStore.getMode(applicationContext) else null
                    val lastFile = ServiceConfig.lastSavedFile
                    return LocalRemoteServer.RemoteStatus(
                        monitoring = monitoring,
                        mode = mode,
                        coins = repo.getCoins(),
                        lastFile = lastFile,
                        useBackCamera = ServiceConfig.settings.useBackCamera
                    )
                }

                override fun onStart(): LocalRemoteServer.RemoteResult {
                    if (MonitoringStateStore.isExpected(applicationContext)) {
                        return LocalRemoteServer.RemoteResult(false, "Monitoring sudah berjalan")
                    }
                    val settings = ServiceConfig.settings
                    if (!hasPermission(android.Manifest.permission.CAMERA)) {
                        return LocalRemoteServer.RemoteResult(false, "Izin kamera belum diberikan")
                    }
                    if (settings.recordAudio && !hasPermission(android.Manifest.permission.RECORD_AUDIO)) {
                        return LocalRemoteServer.RemoteResult(false, "Izin mikrofon belum diberikan")
                    }
                    val repo = CoinRepository.get(applicationContext)
                    if (!repo.spendCoin(1)) {
                        return LocalRemoteServer.RemoteResult(false, "Koin tidak cukup")
                    }
                    val intent = Intent(applicationContext, BackgroundMonitorService::class.java).apply {
                        action = BackgroundMonitorService.ACTION_START
                        putExtra(BackgroundMonitorService.EXTRA_USE_BACK, settings.useBackCamera)
                        putExtra(BackgroundMonitorService.EXTRA_RECORD_AUDIO, settings.recordAudio)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(applicationContext, intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                    return LocalRemoteServer.RemoteResult(true, "Monitoring dimulai (BG)")
                }

                override fun onStop(): LocalRemoteServer.RemoteResult {
                    if (!MonitoringStateStore.isExpected(applicationContext)) {
                        return LocalRemoteServer.RemoteResult(false, "Monitoring belum berjalan")
                    }
                    val intent = Intent(applicationContext, BackgroundMonitorService::class.java).apply {
                        action = BackgroundMonitorService.ACTION_STOP
                    }
                    applicationContext.startService(intent)
                    return LocalRemoteServer.RemoteResult(true, "Monitoring dihentikan")
                }

                override fun onGetSchedule(): LocalRemoteServer.ScheduleSettings {
                    val settings = ServiceConfig.settings
                    return LocalRemoteServer.ScheduleSettings(
                        enabled = settings.scheduleEnabled,
                        startMinutes = settings.scheduleStartMinutes,
                        endMinutes = settings.scheduleEndMinutes,
                        backgroundOnly = settings.scheduleBackgroundOnly
                    )
                }

                override fun onUpdateSchedule(schedule: LocalRemoteServer.ScheduleSettings): LocalRemoteServer.RemoteResult {
                    val updated = ServiceConfig.settings.copy(
                        scheduleEnabled = schedule.enabled,
                        scheduleStartMinutes = schedule.startMinutes,
                        scheduleEndMinutes = schedule.endMinutes,
                        scheduleBackgroundOnly = schedule.backgroundOnly
                    )
                    ServiceConfig.settings = updated
                    runBlocking {
                        settingsStore.setSettings(updated)
                    }
                    return LocalRemoteServer.RemoteResult(true, "Jadwal disimpan")
                }

                override fun onToggleCamera(): LocalRemoteServer.RemoteResult {
                    val updated = ServiceConfig.settings.copy(
                        useBackCamera = !ServiceConfig.settings.useBackCamera
                    )
                    ServiceConfig.settings = updated
                    runBlocking {
                        settingsStore.setSettings(updated)
                    }
                    val mode = MonitoringStateStore.getMode(applicationContext)
                    if (MonitoringStateStore.isExpected(applicationContext) && mode == "bg") {
                        val intent = Intent(applicationContext, BackgroundMonitorService::class.java).apply {
                            action = BackgroundMonitorService.ACTION_SWITCH_CAMERA
                            putExtra(BackgroundMonitorService.EXTRA_USE_BACK, updated.useBackCamera)
                            putExtra(BackgroundMonitorService.EXTRA_RECORD_AUDIO, updated.recordAudio)
                        }
                        applicationContext.startService(intent)
                        return LocalRemoteServer.RemoteResult(true, "Kamera diganti (BG)")
                    }
                    return LocalRemoteServer.RemoteResult(true, "Kamera disimpan (berlaku saat start)")
                }
            })
        }
        server?.stop()
        server?.start(port, pin)
    }

    private fun startSnapshotMonitor() {
        if (snapshotJob?.isActive == true) return
        snapshotJob = lifecycleScope.launch {
            while (isActive) {
                manageSnapshotCamera()
                delay(2000)
            }
        }
    }

    private suspend fun manageSnapshotCamera() {
        val settings = ServiceConfig.settings
        val shouldRun = settings.remoteControlEnabled &&
            !MonitoringStateStore.isExpected(applicationContext) &&
            !MonitoringStateStore.isPreviewActive(applicationContext)
        if (shouldRun) {
            if (snapshotController == null) {
                snapshotController = SnapshotCameraController(this, this)
            }
            try {
                snapshotController?.start(settings.useBackCamera)
            } catch (_: Exception) {
                snapshotController?.stop()
            }
        } else {
            snapshotController?.stop()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        snapshotJob?.cancel()
        snapshotController?.stop()
        snapshotController = null
        server?.stop()
        server = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.smartmotionrecorder.action.REMOTE_START"
        const val ACTION_STOP = "com.smartmotionrecorder.action.REMOTE_STOP"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PIN = "extra_pin"
    }
}
