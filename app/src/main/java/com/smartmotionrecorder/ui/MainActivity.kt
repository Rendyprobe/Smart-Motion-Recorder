package com.smartmotionrecorder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smartmotionrecorder.camera.MotionCameraController
import com.smartmotionrecorder.drive.SafUploader
import com.smartmotionrecorder.service.BackgroundMonitorService
import com.smartmotionrecorder.service.RemoteControlService
import com.smartmotionrecorder.service.ServiceConfig
import com.smartmotionrecorder.ui.theme.MotionRecorderTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartmotionrecorder.coin.CoinViewModel
import com.smartmotionrecorder.ads.RewardedAdManager
import com.smartmotionrecorder.data.MonitoringSettings
import com.smartmotionrecorder.data.SettingsDataStore
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smartmotionrecorder.util.MonitoringStateStore
import com.smartmotionrecorder.util.TamperWatchdogWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionRecorderTheme {
                val coinViewModel: CoinViewModel = viewModel()
                val coins by coinViewModel.coins.collectAsState()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current
                var previewProvider by remember { mutableStateOf<androidx.camera.core.Preview.SurfaceProvider?>(null) }
                var controller by remember { mutableStateOf<MotionCameraController?>(null) }

                var isMonitoring by remember { mutableStateOf(false) }
                var isRecording by remember { mutableStateOf(false) }
                var lastFile by remember { mutableStateOf<String?>(null) }
                var motionRatio by remember { mutableStateOf(0f) }
                var motionAvg by remember { mutableStateOf(0f) }
                val logs = remember { mutableStateListOf<String>() }

                var useBackCamera by remember { mutableStateOf(true) }
                val recordAudio = true
                var lastBindUseBack by remember { mutableStateOf(true) }
                val settingsStore = remember { SettingsDataStore(context) }
                val storedSettings by settingsStore.settingsFlow.collectAsState(initial = MonitoringSettings())
                var settings by remember { mutableStateOf(storedSettings) }
                var showSettings by remember { mutableStateOf(false) }
                var isBackgroundMonitoring by remember { mutableStateOf(false) }
                var bgServiceRunning by remember { mutableStateOf(false) }
                var appVisible by remember { mutableStateOf(true) }
                var pendingRebind by remember { mutableStateOf(false) }
                var bindingInProgress by remember { mutableStateOf(false) }
                var pendingMonitoringEnabled by remember { mutableStateOf<Boolean?>(null) }
                var torchEnabled by remember { mutableStateOf(false) }
                var torchAvailable by remember { mutableStateOf(false) }
                var scheduleAttempted by remember { mutableStateOf(false) }
                var scheduleInWindow by remember { mutableStateOf(false) }
                val neededPermissions = remember(recordAudio) {
                    buildList {
                        add(Manifest.permission.CAMERA)
                        if (recordAudio) add(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                var pendingStart by remember { mutableStateOf(false) }
                var pendingStartBackground by remember { mutableStateOf(false) }
                var pendingStartBackgroundWithCoin by remember { mutableStateOf(false) }
                var lastError by remember { mutableStateOf<String?>(null) }
                lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
                val signInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { /* no-op placeholder, not used for SAF */ }
                var adReady by remember { mutableStateOf(false) }
                var adLoading by remember { mutableStateOf(false) }
                val rewardedAdManager = remember {
                    RewardedAdManager(context, object : RewardedAdManager.Listener {
                        override fun onRewardEarned(amount: Int) {
                            coinViewModel.onWatchAdReward()
                            adReady = false
                            adLoading = false
                        }

                        override fun onAdFailed(message: String) {
                            adReady = false
                            adLoading = false
                            lastError = message
                        }

                        override fun onAdClosed() {
                            adReady = false
                            adLoading = false
                        }

                        override fun onAdLoaded() {
                            adReady = true
                            adLoading = false
                        }
                    })
                }

                fun missingPermissions(): List<String> = neededPermissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }

                fun requestPerms() {
                    val miss = missingPermissions()
                    if (miss.isNotEmpty()) {
                        permissionLauncher.launch(miss.toTypedArray())
                    }
                }

                fun applySettings(newSettings: MonitoringSettings) {
                    settings = newSettings
                    scope.launch {
                        settingsStore.setSettings(newSettings)
                    }
                }

                fun stopMonitoring(reason: String = "manual", updateState: Boolean = true) {
                    isMonitoring = false
                    isRecording = false
                    controller?.setMonitoringEnabled(false)
                    motionRatio = 0f
                    motionAvg = 0f
                    logs.add("Monitoring stopped")
                    if (updateState) {
                        MonitoringStateStore.markMonitoringStopped(context, reason, expectedStop = true)
                    }
                }

                fun ensureController(): MotionCameraController {
                    if (controller == null) {
                        controller = MotionCameraController(
                            context = context,
                            lifecycleOwner = this@MainActivity,
                            callback = object : MotionCameraController.Callback {
                                override fun onMotion(ratio: Float, avg: Float) {
                                    motionRatio = ratio
                                    motionAvg = avg
                                }

                                override fun onMotionTrigger() {
                                    logs.add("Motion detected")
                                }

                                override fun onRecordingStarted(path: String) {
                                    isRecording = true
                                    lastFile = path
                                    ServiceConfig.lastSavedFile = path
                                    logs.add("Recording started: ${File(path).name}")
                                }

                                override fun onRecordingStopped(path: String?) {
                                    isRecording = false
                                    path?.let { logs.add("Recording stopped: ${File(it).name}") }
                                    if (path != null && settings.uploadToDrive && settings.safFolderUri.isNotBlank()) {
                                        SafUploader.enqueueCopy(
                                            context = context,
                                            filePath = path,
                                            treeUri = settings.safFolderUri,
                                            wifiOnly = settings.uploadWifiOnly
                                        )
                                        logs.add("Upload dijadwalkan ke folder SAF")
                                    }
                                }

                                override fun onError(message: String) {
                                    logs.add("Error: $message")
                                    lastError = message
                                }
                            }
                        )
                    }
                    return controller!!
                }

                fun bindCamera(monitoringEnabled: Boolean): Boolean {
                    val provider = previewProvider
                    if (provider == null) {
                        lastError = "Preview belum siap, tunggu sebentar atau ulangi."
                        return false
                    }
                    if (bindingInProgress) {
                        pendingMonitoringEnabled = monitoringEnabled
                        return true
                    }
                    bindingInProgress = true
                    pendingMonitoringEnabled = null
                    isMonitoring = monitoringEnabled
                    scope.launch {
                        val instance = ensureController()
                        instance.start(provider, useBackCamera, recordAudio, settings, monitoringEnabled)
                        lastBindUseBack = useBackCamera
                        MonitoringStateStore.setPreviewActive(context, true)
                        if (monitoringEnabled && !MonitoringStateStore.isExpected(context)) {
                            MonitoringStateStore.markMonitoringStarted(context, "fg")
                        }
                        if (monitoringEnabled) {
                            lastError = null
                        }
                        torchAvailable = instance.hasFlashUnit()
                        if (torchEnabled && torchAvailable) {
                            instance.setTorchEnabled(true)
                        } else if (!torchAvailable) {
                            torchEnabled = false
                        }
                        bindingInProgress = false
                        val next = pendingMonitoringEnabled
                        if (next != null && next != monitoringEnabled) {
                            pendingMonitoringEnabled = null
                            bindCamera(next)
                        }
                    }
                    return true
                }

                fun startBackgroundMonitoringInternal(logStart: Boolean): Boolean {
                    if (torchEnabled) {
                        controller?.setTorchEnabled(false)
                        torchEnabled = false
                    }
                    torchAvailable = false
                    stopMonitoring("handoff", updateState = false)
                    controller?.stop()
                    MonitoringStateStore.setPreviewActive(context, false)
                    ServiceConfig.settings = settings
                    val intent = Intent(context, BackgroundMonitorService::class.java).apply {
                        action = BackgroundMonitorService.ACTION_START
                        putExtra(BackgroundMonitorService.EXTRA_USE_BACK, useBackCamera)
                        putExtra(BackgroundMonitorService.EXTRA_RECORD_AUDIO, recordAudio)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                    bgServiceRunning = true
                    isBackgroundMonitoring = true
                    if (logStart) {
                        logs.add("Start monitoring (background)")
                    }
                    return true
                }

                fun startBackgroundMonitoringWithCoin(logStart: Boolean): Boolean {
                    val miss = missingPermissions()
                    if (miss.isNotEmpty()) {
                        pendingStartBackground = true
                        pendingStartBackgroundWithCoin = true
                        permissionLauncher.launch(miss.toTypedArray())
                        return false
                    }
                    val success = coinViewModel.tryStartMonitoring {
                        startBackgroundMonitoringInternal(logStart)
                    }
                    if (!success) {
                        lastError = "Koin tidak cukup atau start gagal"
                    } else {
                        lastError = null
                    }
                    return success
                }

                fun startBackgroundMonitoring() {
                    val miss = missingPermissions()
                    if (miss.isNotEmpty()) {
                        pendingStartBackground = true
                        pendingStartBackgroundWithCoin = false
                        permissionLauncher.launch(miss.toTypedArray())
                        return
                    }
                    isBackgroundMonitoring = true
                    if (appVisible) {
                        bindCamera(true)
                        lastError = null
                        logs.add("BG mode aktif (foreground)")
                    } else {
                        startBackgroundMonitoringInternal(true)
                        lastError = null
                    }
                }

                fun stopBackgroundService(log: Boolean) {
                    val intent = Intent(context, BackgroundMonitorService::class.java).apply {
                        action = BackgroundMonitorService.ACTION_STOP
                    }
                    if (bgServiceRunning) {
                        context.startService(intent)
                        bgServiceRunning = false
                    }
                    if (log) {
                        logs.add("Stop monitoring (background)")
                    }
                }

                fun stopBackgroundMonitoring() {
                    isBackgroundMonitoring = false
                    stopBackgroundService(true)
                    if (isMonitoring) {
                        stopMonitoring("manual")
                    } else if (!bgServiceRunning) {
                        bindCamera(false)
                    }
                }

                fun startMonitoringInternal(): Boolean {
                    val success = bindCamera(true)
                    if (success) {
                        MonitoringStateStore.markMonitoringStarted(context, "fg")
                    }
                    return success
                }

                fun startMonitoring() {
                    val miss = missingPermissions()
                    if (miss.isNotEmpty()) {
                        pendingStart = true
                        permissionLauncher.launch(miss.toTypedArray())
                        return
                    }
                    if (isBackgroundMonitoring) {
                        stopBackgroundMonitoring()
                    }
                    val success = coinViewModel.tryStartMonitoring {
                        startMonitoringInternal()
                    }
                    if (!success) {
                        pendingStart = false
                        lastError = "Koin tidak cukup atau start gagal"
                    } else {
                        lastError = null
                        logs.add("Monitoring dimulai (koin terpakai 1)")
                    }
                }

                fun isWithinScheduleWindow(): Boolean {
                    val now = java.util.Calendar.getInstance().let {
                        it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
                    }
                    val start = settings.scheduleStartMinutes
                    val end = settings.scheduleEndMinutes
                    return if (start <= end) {
                        now in start until end
                    } else {
                        now >= start || now < end
                    }
                }

                permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    val miss = missingPermissions()
                    if (miss.isEmpty()) {
                        if (pendingStartBackground) {
                            val useCoin = pendingStartBackgroundWithCoin
                            pendingStartBackground = false
                            pendingStartBackgroundWithCoin = false
                            pendingStart = false
                            if (useCoin) {
                                if (!settings.scheduleEnabled || !isWithinScheduleWindow()) {
                                    logs.add("Jadwal batal: di luar waktu")
                                    return@rememberLauncherForActivityResult
                                }
                                startBackgroundMonitoringWithCoin(true)
                            } else {
                                startBackgroundMonitoring()
                            }
                        } else if (pendingStart) {
                            pendingStart = false
                            startMonitoring()
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    requestPerms()
                    adLoading = true
                    rewardedAdManager.initialize()
                    MonitoringStateStore.consumeTamperMessage(context)?.let {
                        logs.add(it)
                        lastError = it
                    }
                }

                LaunchedEffect(storedSettings) {
                    if (settings != storedSettings) {
                        settings = storedSettings
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                                appVisible = true
                                MonitoringStateStore.setPreviewActive(context, true)
                                if (isBackgroundMonitoring && bgServiceRunning) {
                                    pendingRebind = true
                                    stopBackgroundService(false)
                                    scope.launch {
                                        delay(600)
                                        pendingRebind = false
                                        bindCamera(true)
                                    }
                                }
                            }
                            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> {
                                appVisible = false
                                pendingRebind = false
                                MonitoringStateStore.setPreviewActive(context, false)
                                if (isBackgroundMonitoring && !bgServiceRunning) {
                                    startBackgroundMonitoringInternal(false)
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(settings) {
                    ServiceConfig.settings = settings
                    MonitoringStateStore.setAntiTamperEnabled(context, settings.antiTamperEnabled)
                }

                LaunchedEffect(settings.antiTamperEnabled) {
                    val workManager = WorkManager.getInstance(context)
                    if (settings.antiTamperEnabled) {
                        val request = PeriodicWorkRequestBuilder<TamperWatchdogWorker>(
                            15, TimeUnit.MINUTES
                        ).build()
                        workManager.enqueueUniquePeriodicWork(
                            "tamper_watchdog",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            request
                        )
                    } else {
                        workManager.cancelUniqueWork("tamper_watchdog")
                    }
                }

                LaunchedEffect(
                    settings.remoteControlEnabled,
                    settings.remoteControlPort,
                    settings.remoteControlPin
                ) {
                    ServiceConfig.settings = settings
                    val intent = Intent(context, RemoteControlService::class.java).apply {
                        if (settings.remoteControlEnabled) {
                            action = RemoteControlService.ACTION_START
                            putExtra(RemoteControlService.EXTRA_PORT, settings.remoteControlPort)
                            putExtra(RemoteControlService.EXTRA_PIN, settings.remoteControlPin)
                        } else {
                            action = RemoteControlService.ACTION_STOP
                        }
                    }
                    if (settings.remoteControlEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, intent)
                        } else {
                            context.startService(intent)
                        }
                    } else {
                        context.startService(intent)
                    }
                }

                LaunchedEffect(
                    settings,
                    isMonitoring,
                    previewProvider,
                    bgServiceRunning,
                    appVisible,
                    pendingRebind,
                    isBackgroundMonitoring,
                    bindingInProgress
                ) {
                    if (previewProvider != null && !bgServiceRunning && appVisible && !pendingRebind && !bindingInProgress) {
                        val shouldMonitor = isMonitoring || (isBackgroundMonitoring && appVisible)
                        bindCamera(shouldMonitor)
                    }
                }

                LaunchedEffect(
                    settings.scheduleEnabled,
                    settings.scheduleStartMinutes,
                    settings.scheduleEndMinutes,
                    settings.scheduleBackgroundOnly
                ) {
                    scheduleAttempted = false
                    scheduleInWindow = false
                    if (!settings.scheduleEnabled) return@LaunchedEffect
                    while (isActive && settings.scheduleEnabled) {
                        val inWindow = isWithinScheduleWindow()
                        if (inWindow) {
                            if (!scheduleInWindow) {
                                scheduleInWindow = true
                                scheduleAttempted = false
                            }
                            if (!scheduleAttempted && !isMonitoring && !bgServiceRunning) {
                                val useBackground = settings.scheduleBackgroundOnly
                                val success = if (useBackground) {
                                    startBackgroundMonitoringWithCoin(true)
                                } else {
                                    coinViewModel.tryStartMonitoring { bindCamera(true) }
                                }
                                if (success) {
                                    logs.add(
                                        if (useBackground) "Jadwal aktif: monitoring dimulai (BG)"
                                        else "Jadwal aktif: monitoring dimulai"
                                    )
                                    scheduleAttempted = true
                                    lastError = null
                                } else if (lastError?.contains("Preview belum siap") == true) {
                                    // retry later
                                } else {
                                    scheduleAttempted = true
                                    lastError = "Jadwal gagal: koin/izin belum siap"
                                }
                            }
                        } else {
                            if (scheduleInWindow) {
                                logs.add("Jadwal selesai: monitoring berhenti")
                            }
                            scheduleInWindow = false
                            scheduleAttempted = false
                            if (isMonitoring) {
                                stopMonitoring("schedule")
                            }
                            if (bgServiceRunning) {
                                stopBackgroundService(false)
                            }
                        }
                        delay(30_000)
                    }
                }

                LaunchedEffect(settings.torchUiEnabled) {
                    if (!settings.torchUiEnabled && torchEnabled) {
                        controller?.setTorchEnabled(false)
                        torchEnabled = false
                    }
                }

                LaunchedEffect(settings.antiTamperEnabled, isMonitoring, bgServiceRunning) {
                    if (!settings.antiTamperEnabled) return@LaunchedEffect
                    if (!isMonitoring || bgServiceRunning) return@LaunchedEffect
                    while (isActive) {
                        MonitoringStateStore.heartbeat(context)
                        delay(30_000)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    isMonitoring = isMonitoring,
                    isRecording = isRecording,
                    isBackgroundMonitoring = isBackgroundMonitoring,
                    coins = coins,
                    adReady = adReady,
                    adLoading = adLoading,
                    torchEnabled = torchEnabled,
                    torchAvailable = torchAvailable,
                    torchUiEnabled = settings.torchUiEnabled,
                    scheduleEnabled = settings.scheduleEnabled,
                    scheduleStartMinutes = settings.scheduleStartMinutes,
                    scheduleEndMinutes = settings.scheduleEndMinutes,
                    motionRatio = motionAvg,
                    lastFile = lastFile,
                    errorMessage = lastError,
                    logs = logs,
                        missingPermissions = missingPermissions(),
                        requestPermissions = { requestPerms() },
                        onStartMonitoring = { startMonitoring() },
                        onStopMonitoring = { stopMonitoring() },
                        onStartBackground = { startBackgroundMonitoring() },
                        onStopBackground = { stopBackgroundMonitoring() },
                        onToggleCamera = {
                            useBackCamera = !useBackCamera
                            if (bgServiceRunning) {
                                stopBackgroundService(false)
                                startBackgroundMonitoringInternal(true)
                            } else {
                                if (torchEnabled) {
                                    controller?.setTorchEnabled(false)
                                    torchEnabled = false
                                }
                                bindCamera(isMonitoring)
                            }
                        },
                    onToggleTorch = {
                        val next = !torchEnabled
                        val updated = controller?.setTorchEnabled(next) == true
                        if (updated) {
                            torchEnabled = next
                        }
                    },
                    onOpenSettings = { showSettings = true },
                    onScheduleStartChange = { minutes ->
                        applySettings(settings.copy(scheduleStartMinutes = minutes))
                    },
                    onScheduleEndChange = { minutes ->
                        applySettings(settings.copy(scheduleEndMinutes = minutes))
                    },
                    onShareLastVideo = { path -> shareLastVideo(path) },
                    onPreviewReady = { provider ->
                        if (previewProvider === provider) return@MainScreen
                        previewProvider = provider
                        MonitoringStateStore.setPreviewActive(context, true)
                        if (!bgServiceRunning && !bindingInProgress) {
                            val shouldMonitor = isMonitoring || (isBackgroundMonitoring && appVisible)
                            bindCamera(shouldMonitor)
                        }
                    },
                        onWatchAd = {
                            adLoading = true
                            rewardedAdManager.show(this@MainActivity)
                        },
                        onRefreshAd = {
                            adLoading = true
                            rewardedAdManager.loadAd()
                        }
                    )
                    if (showSettings) {
                        SettingsScreen(
                            settings = settings,
                            onSettingsChange = { applySettings(it) },
                            onBack = { showSettings = false }
                        )
                    }
                }
            }
        }
    }
}

private fun ComponentActivity.shareLastVideo(path: String) {
    val file = File(path)
    if (!file.exists()) return
    val authority = "${applicationContext.packageName}.fileprovider"
    val uri = androidx.core.content.FileProvider.getUriForFile(this, authority, file)
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(android.content.Intent.createChooser(intent, "Bagikan video terakhir"))
}
