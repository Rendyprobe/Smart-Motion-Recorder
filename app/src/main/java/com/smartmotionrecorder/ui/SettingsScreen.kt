package com.smartmotionrecorder.ui

import android.os.Build
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.smartmotionrecorder.data.MonitoringSettings
import com.smartmotionrecorder.data.StorageMode
import kotlinx.coroutines.launch
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun SettingsScreen(
    settings: MonitoringSettings,
    onSettingsChange: (MonitoringSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSyncInfo by remember { mutableStateOf(false) }
    val localIp = remember { getLocalIpAddress() }
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Text("Kembali")
                }
                Text(
                    text = "Pengaturan",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sensitivitas deteksi", style = MaterialTheme.typography.titleSmall)
                    SliderRow(
                        label = "Motion ratio (0.001-0.2)",
                        value = settings.motionRatioThreshold,
                        range = 0.001f..0.2f,
                        steps = 0
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
                        text = "Mode custom akan menyimpan ke Movies/<folder> (Android 10+). Pada Android 7-9 tetap di folder aplikasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Lokasi saat ini: $saveDir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(saveDir))
                            scope.launch {
                                snackbarHostState.showSnackbar("Lokasi folder disalin")
                            }
                        }
                    ) {
                        Text("Salin lokasi folder")
                    }
                    Divider()
                    Text("Otomasi", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jadwal auto start/stop")
                        Switch(
                            checked = settings.scheduleEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(scheduleEnabled = it)) }
                        )
                    }
                    if (settings.scheduleEnabled) {
                        Text(
                            text = "Atur jam di halaman monitoring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Jadwal gunakan BG saja")
                            Switch(
                                checked = settings.scheduleBackgroundOnly,
                                onCheckedChange = { onSettingsChange(settings.copy(scheduleBackgroundOnly = it)) }
                            )
                        }
                        Text(
                            text = "Jika aktif, jadwal akan menjalankan monitoring di latar belakang (tanpa preview).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Divider()
                    Text("Kamera", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto exposure lock")
                        Switch(
                            checked = settings.autoExposureLock,
                            onCheckedChange = { onSettingsChange(settings.copy(autoExposureLock = it)) }
                        )
                    }
                    Text(
                        text = "Mengunci exposure saat monitoring agar perubahan cahaya tidak memicu false motion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider()
                    Text("Keamanan", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Anti-tamper")
                        Switch(
                            checked = settings.antiTamperEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(antiTamperEnabled = it)) }
                        )
                    }
                    Text(
                        text = "Notifikasi muncul jika monitoring berhenti mendadak tanpa aksi pengguna.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Divider()
                    Text("Remote lokal", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Aktifkan kontrol remote")
                        Switch(
                            checked = settings.remoteControlEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(remoteControlEnabled = it)) }
                        )
                    }
                    if (settings.remoteControlEnabled) {
                        OutlinedTextField(
                            value = settings.remoteControlPort.toString(),
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }
                                val portValue = digits.toIntOrNull() ?: settings.remoteControlPort
                                val safePort = portValue.coerceIn(1024, 65535)
                                onSettingsChange(settings.copy(remoteControlPort = safePort))
                            },
                            label = { Text("Port (1024-65535)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = settings.remoteControlPin,
                            onValueChange = { onSettingsChange(settings.copy(remoteControlPin = it.trim())) },
                            label = { Text("PIN (opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            text = if (localIp != null) {
                                "Akses dari browser: http://$localIp:${settings.remoteControlPort}"
                            } else {
                                "Akses dari browser: http://IP-HP:${settings.remoteControlPort}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Jika PIN diisi, tambahkan ?pin=PIN pada URL atau header X-PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Start dari remote tetap mengurangi 1 koin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Divider()
                    Text("Tampilan", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tampilkan ikon senter")
                        Switch(
                            checked = settings.torchUiEnabled,
                            onCheckedChange = { onSettingsChange(settings.copy(torchUiEnabled = it)) }
                        )
                    }
                    Divider()
                    Text("Sinkronisasi cloud (eksternal)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Gunakan aplikasi sync seperti FolderSync/Autosync untuk mengunggah otomatis ke cloud (mis. Google Drive).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Arahkan aplikasi sync ke folder lokal di atas, pilih mode Upload only, lalu aktifkan sync di background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showSyncInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Cara kerja sinkronisasi")
                    }
                }
            }
            if (showSyncInfo) {
                AlertDialog(
                    onDismissRequest = { showSyncInfo = false },
                    title = { Text("Mekanisme upload (opsi 2)") },
                    text = {
                        Text(
                            "- Aplikasi menyimpan video ke folder lokal.\n" +
                                "- Aplikasi sync (FolderSync/Autosync) mengunggah ke cloud.\n" +
                                "- Pilih mode Upload only agar tidak mengunduh ulang.\n" +
                                "- Pastikan folder lokal = lokasi rekaman di atas."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showSyncInfo = false }) {
                            Text("Mengerti")
                        }
                    }
                )
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

private fun getLocalIpAddress(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addresses = intf.inetAddresses
            for (addr in addresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress
                }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}
