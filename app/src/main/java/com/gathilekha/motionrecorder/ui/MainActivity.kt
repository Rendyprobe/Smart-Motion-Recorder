package com.gathilekha.motionrecorder.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.gathilekha.motionrecorder.camera.MotionCameraController
import com.gathilekha.motionrecorder.drive.DriveUploader
import com.gathilekha.motionrecorder.drive.SafUploader
import com.gathilekha.motionrecorder.service.BackgroundMonitorService
import com.gathilekha.motionrecorder.service.ServiceConfig
import com.gathilekha.motionrecorder.ui.theme.MotionRecorderTheme
import android.util.Log
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gathilekha.motionrecorder.coin.CoinViewModel
import com.gathilekha.motionrecorder.ads.RewardedAdManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionRecorderTheme {
                val coinViewModel: CoinViewModel = viewModel()
                val coins by coinViewModel.coins.collectAsState()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
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
                var settings by remember { mutableStateOf(com.gathilekha.motionrecorder.data.MonitoringSettings()) }
                var isBackgroundMonitoring by remember { mutableStateOf(false) }
                val openTreeLauncher = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        settings = settings.copy(safFolderUri = uri.toString(), uploadToDrive = true, driveAccount = "Folder SAF")
                        logs.add("Folder cloud dipilih")
                    }
                }

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

                fun stopMonitoring() {
                    isMonitoring = false
                    isRecording = false
                    controller?.stop()
                    logs.add("Monitoring stopped")
                }

                fun startBackgroundMonitoring() {
                    val miss = missingPermissions()
                    if (miss.isNotEmpty()) {
                        pendingStart = true
                        permissionLauncher.launch(miss.toTypedArray())
                        return
                    }
                    stopMonitoring()
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
                    isBackgroundMonitoring = true
                    logs.add("Start monitoring (background)")
                }

                fun stopBackgroundMonitoring() {
                    val intent = Intent(context, BackgroundMonitorService::class.java).apply {
                        action = BackgroundMonitorService.ACTION_STOP
                    }
                    context.startService(intent)
                    isBackgroundMonitoring = false
                    logs.add("Stop monitoring (background)")
                }

                fun startMonitoringInternal(): Boolean {
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
                    return if (previewProvider != null) {
                        scope.launch {
                            controller?.start(previewProvider!!, useBackCamera, recordAudio, settings)
                            lastBindUseBack = useBackCamera
                            isMonitoring = true
                        }
                        true
                    } else {
                        lastError = "Preview belum siap, tunggu sebentar atau ulangi."
                        false
                    }
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
                        logs.add("Monitoring dimulai (koin terpakai 1)")
                    }
                }

                permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    val miss = missingPermissions()
                    if (miss.isEmpty() && pendingStart) {
                        pendingStart = false
                        startMonitoring()
                    }
                }

                LaunchedEffect(Unit) {
                    requestPerms()
                    adLoading = true
                    rewardedAdManager.initialize()
                }

                LaunchedEffect(settings, isMonitoring, previewProvider) {
                    if (isMonitoring && previewProvider != null) {
                        controller?.start(previewProvider!!, useBackCamera, recordAudio, settings)
                    }
                }

                MainScreen(
                    modifier = Modifier.fillMaxSize(),
                    isMonitoring = isMonitoring,
                    isRecording = isRecording,
                    isBackgroundMonitoring = isBackgroundMonitoring,
                    coins = coins,
                    adReady = adReady,
                    adLoading = adLoading,
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
                        if (isMonitoring) startMonitoring()
                    },
                    settings = settings,
                    onSettingsChange = { settings = it },
                    onShareLastVideo = { path -> shareLastVideo(path) },
                    onPreviewReady = { provider ->
                        previewProvider = provider
                        if (isMonitoring) {
                            scope.launch { controller?.start(provider, useBackCamera, recordAudio, settings) }
                        }
                    },
                    onDriveSignIn = { openTreeLauncher.launch(null) },
                    onDriveSignOut = {
                        settings = settings.copy(driveAccount = "", uploadToDrive = false, safFolderUri = "")
                        logs.add("Folder cloud dibersihkan")
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
