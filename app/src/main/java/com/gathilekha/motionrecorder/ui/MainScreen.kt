package com.gathilekha.motionrecorder.ui

import android.os.Build
import android.os.Environment
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.gathilekha.motionrecorder.data.StorageMode
import java.io.File

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isMonitoring: Boolean,
    isRecording: Boolean,
    isBackgroundMonitoring: Boolean,
    coins: Int,
    adReady: Boolean,
    adLoading: Boolean,
    motionRatio: Float,
    lastFile: String?,
    errorMessage: String?,
    logs: List<String>,
    missingPermissions: List<String>,
    requestPermissions: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onStartBackground: () -> Unit,
    onStopBackground: () -> Unit,
    onToggleCamera: () -> Unit,
    settings: com.gathilekha.motionrecorder.data.MonitoringSettings,
    onSettingsChange: (com.gathilekha.motionrecorder.data.MonitoringSettings) -> Unit,
    onShareLastVideo: (String) -> Unit,
    onPreviewReady: (androidx.camera.core.Preview.SurfaceProvider) -> Unit,
    onDriveSignIn: () -> Unit,
    onDriveSignOut: () -> Unit,
    onWatchAd: () -> Unit,
    onRefreshAd: () -> Unit
) {
    val context = LocalContext.current
    val saveDir = run {
        val folder = settings.storageFolderName.ifBlank { "MotionRecorder" }
        when (settings.storageMode) {
            StorageMode.APP_PRIVATE -> File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), folder).absolutePath
            StorageMode.PUBLIC_MEDIASTORE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${Environment.DIRECTORY_MOVIES}/$folder"
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), folder).absolutePath
            }
        }
    }

    Surface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF0B1021),
                            Color(0xFF0D1B2A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                    containerColor = if (missingPermissions.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Status kamera",
                        style = MaterialTheme.typography.titleMedium
                    )
                    val statusText = buildString {
                        when {
                            missingPermissions.isNotEmpty() -> {
                                append("Izin kamera")
                                if (missingPermissions.any { it.contains("AUDIO") }) append(" & mikrofon")
                                append(" diperlukan.")
                            }
                            isRecording -> append("Rekaman berjalan")
                            isMonitoring -> append("Monitoring aktif (menunggu gerakan)")
                            else -> append("Idle")
                        }
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                    if (isMonitoring) {
                        Text(
                            text = "Monitoring aktif - tekan Stop untuk berhenti.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (missingPermissions.isNotEmpty()) {
                        TextButton(onClick = { requestPermissions() }) { Text("Grant permissions") }
                        Text(
                            text = "Izin kamera & mikrofon diperlukan untuk preview dan rekam otomatis. Pemrosesan hanya di perangkat, tidak ada unggah ke server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        Text(
                            text = "Pemrosesan gerakan berlangsung lokal di perangkat, transparan melalui notifikasi foreground.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box {
                    AndroidView(
                        factory = {
                            PreviewView(context).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                onPreviewReady(surfaceProvider)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view -> onPreviewReady(view.surfaceProvider) }
                    )
                    Text(
                        text = "Live preview",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (missingPermissions.isNotEmpty()) {
                        Text(
                            text = "Izin kamera/audio diperlukan",
                            color = Color.Yellow,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Koin: $coins",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Status: ${if (isRecording) "RECORDING" else if (isMonitoring) "MONITORING" else if (isBackgroundMonitoring) "BG MONITORING" else "IDLE"}",
                        color = if (isRecording) Color(0xFFB71C1C) else if (isMonitoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "ratio=${"%.3f".format(motionRatio)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    lastFile?.let {
                        Text(
                            text = "Last file: ${it.substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "Storage: ${if (settings.storageMode == StorageMode.APP_PRIVATE) "App folder" else "Custom (SAF)"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(text = "Videos: $saveDir", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = { if (isMonitoring) onStopMonitoring() else onStartMonitoring() }) {
                        Text(if (isMonitoring) "Stop Monitoring" else "Start Monitoring")
                    }
                    if (missingPermissions.isNotEmpty()) {
                        TextButton(onClick = { requestPermissions() }) { Text("Grant permissions") }
                    }
                    Button(onClick = { if (adReady && !adLoading) onWatchAd() else onRefreshAd() }) {
                        Text(
                            when {
                                adLoading -> "Loading iklan..."
                                adReady -> "Dapatkan Koin (Tonton Iklan)"
                                else -> "Muat ulang iklan"
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = onToggleCamera) { Text("Switch camera") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { if (isBackgroundMonitoring) onStopBackground() else onStartBackground() }
                ) {
                    Text(if (isBackgroundMonitoring) "Stop BG Monitoring" else "Start BG Monitoring")
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pengaturan sensivitas", style = MaterialTheme.typography.titleSmall)
                    SliderRow(
                        label = "Motion ratio (0.01-0.2)",
                        value = settings.motionRatioThreshold,
                        range = 0.01f..0.2f,
                        steps = 18
                    ) { onSettingsChange(settings.copy(motionRatioThreshold = it)) }
                    SliderRow(
                        label = "Pixel diff (5-80)",
                        value = settings.pixelDiffThreshold.toFloat(),
                        range = 5f..80f,
                        steps = 15
                    ) { onSettingsChange(settings.copy(pixelDiffThreshold = it.toInt())) }
                    SliderRow(
                        label = "Consecutive frames (1-5)",
                        value = settings.consecutiveMotionFrames.toFloat(),
                        range = 1f..5f,
                        steps = 3
                    ) { onSettingsChange(settings.copy(consecutiveMotionFrames = it.toInt())) }
                    SliderRow(
                        label = "Analyze every N frame (1-5)",
                        value = settings.analyzeEveryNthFrame.toFloat(),
                        range = 1f..5f,
                        steps = 3
                    ) { onSettingsChange(settings.copy(analyzeEveryNthFrame = it.toInt())) }
                    SliderRow(
                        label = "Downsample stride (2-8)",
                        value = settings.downsampleStride.toFloat(),
                        range = 2f..8f,
                        steps = 6
                    ) { onSettingsChange(settings.copy(downsampleStride = it.toInt())) }
                    SliderRow(
                        label = "Stop delay tanpa gerakan (1-10 menit)",
                        value = settings.stopDelayMs.toFloat(),
                        range = 60_000f..600_000f,
                        steps = 8
                    ) {
                        onSettingsChange(settings.copy(stopDelayMs = it.toInt()))
                    }
                    Text(
                        text = "Saat tidak ada gerakan selama ~${(settings.stopDelayMs / 1000f / 60f).coerceAtLeast(1f).format(1)} menit, rekaman dihentikan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider()
                    Text("Penyimpanan", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StorageToggle(
                            label = "App (private)",
                            selected = settings.storageMode == StorageMode.APP_PRIVATE,
                            onClick = { onSettingsChange(settings.copy(storageMode = StorageMode.APP_PRIVATE)) },
                            modifier = Modifier.weight(1f)
                        )
                        StorageToggle(
                            label = "Custom (MediaStore)",
                            selected = settings.storageMode == StorageMode.PUBLIC_MEDIASTORE,
                            onClick = { onSettingsChange(settings.copy(storageMode = StorageMode.PUBLIC_MEDIASTORE)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = settings.storageFolderName,
                        onValueChange = { onSettingsChange(settings.copy(storageFolderName = it)) },
                        label = { Text("Nama folder") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "Mode custom akan menyimpan ke Movies/<folder> (Android 10+). Pada Android 7-9 tetap di folder aplikasi. Nama folder bisa bebas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Divider()
                    Text("Upload ke cloud (tanpa login GCP)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (settings.safFolderUri.isBlank()) "Pilih folder (mis. Drive) lewat penyimpanan perangkat. Tidak perlu kartu kredit/OAuth." else "Folder dipilih: ${settings.safFolderUri.take(40)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Upload koin: tombol di atas untuk tonton iklan +5 koin. Monitoring -1 koin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        Text("Aktifkan upload otomatis")
                        Switch(
                            checked = settings.uploadToDrive,
                            onCheckedChange = { onSettingsChange(settings.copy(uploadToDrive = it)) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Hanya Wi‑Fi")
                        Switch(
                            checked = settings.uploadWifiOnly,
                            onCheckedChange = { onSettingsChange(settings.copy(uploadWifiOnly = it)) }
                        )
                    }
                    Text(
                        text = if (settings.safFolderUri.isBlank()) "Belum pilih folder." else "Folder siap dipakai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (settings.safFolderUri.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onDriveSignIn
                        ) {
                            Text(if (settings.driveAccount.isBlank()) "Sign in Google Drive" else "Ubah akun Drive")
                        }
                        if (settings.driveAccount.isNotBlank()) {
                            TextButton(onClick = onDriveSignOut) {
                                Text("Sign out")
                            }
                        }
                    }
                    if (settings.driveAccount.isNotBlank()) {
                        Text(
                            text = "Terhubung ke: ${settings.driveAccount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Perlu login Google di perangkat. Token memakai GoogleAuthUtil; untuk produksi sebaiknya gunakan Google Sign-In + Drive API resmi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider()
            Text(text = "Event Log", style = MaterialTheme.typography.titleSmall)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                items(logs.reversed()) { entry ->
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                lastFile?.let { path ->
                    Button(onClick = { onShareLastVideo(path) }) {
                        Text("Share last video")
                    }
                } ?: Text(text = "Belum ada rekaman", style = MaterialTheme.typography.bodySmall)
            }
        }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps
        )
    }
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

@Composable
private fun StorageToggle(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label, maxLines = 1)
    }
}
