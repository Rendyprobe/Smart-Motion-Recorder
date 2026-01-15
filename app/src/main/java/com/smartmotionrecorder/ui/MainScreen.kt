package com.smartmotionrecorder.ui

import androidx.camera.view.PreviewView
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    isMonitoring: Boolean,
    isRecording: Boolean,
    isBackgroundMonitoring: Boolean,
    coins: Int,
    adReady: Boolean,
    adLoading: Boolean,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    torchUiEnabled: Boolean,
    scheduleEnabled: Boolean,
    scheduleStartMinutes: Int,
    scheduleEndMinutes: Int,
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
    onToggleTorch: () -> Unit,
    onOpenSettings: () -> Unit,
    onScheduleStartChange: (Int) -> Unit,
    onScheduleEndChange: (Int) -> Unit,
    onShareLastVideo: (String) -> Unit,
    onPreviewReady: (androidx.camera.core.Preview.SurfaceProvider) -> Unit,
    onWatchAd: () -> Unit,
    onRefreshAd: () -> Unit
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    var showAdConfirm by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pengaturan",
                        tint = Color.White
                    )
                }
                CoinBadge(
                    coins = coins,
                    adReady = adReady,
                    adLoading = adLoading,
                    onAddClick = { showAdConfirm = true }
                )
            }
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
                    val statusText = when {
                        isRecording -> "RECORDING"
                        isMonitoring -> "MONITORING"
                        isBackgroundMonitoring -> "BG MONITORING"
                        else -> "IDLE"
                    }
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                    if (missingPermissions.isNotEmpty()) {
                        TextButton(onClick = { requestPermissions() }) { Text("Grant permissions") }
                        Text(
                            text = "Izin kamera & mikrofon diperlukan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
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
                    .height(420.dp),
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
                    if (torchUiEnabled) {
                        val torchTint = when {
                            !torchAvailable -> Color.Gray
                            torchEnabled -> Color(0xFFFFE082)
                            else -> Color.White
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            IconButton(
                                onClick = onToggleTorch,
                                enabled = torchAvailable && missingPermissions.isEmpty()
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Senter",
                                    tint = torchTint
                                )
                            }
                        }
                    }
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

            if (scheduleEnabled) {
                val showPicker = { minutes: Int, onSelected: (Int) -> Unit ->
                    val hour = minutes / 60
                    val minute = minutes % 60
                    TimePickerDialog(
                        context,
                        { _, h, m -> onSelected(h * 60 + m) },
                        hour,
                        minute,
                        is24Hour
                    ).show()
                }
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Jadwal", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TimeChip(
                                label = "Mulai",
                                timeText = formatMinutes(scheduleStartMinutes)
                            ) {
                                showPicker(scheduleStartMinutes, onScheduleStartChange)
                            }
                            TimeChip(
                                label = "Selesai",
                                timeText = formatMinutes(scheduleEndMinutes)
                            ) {
                                showPicker(scheduleEndMinutes, onScheduleEndChange)
                            }
                        }
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
                        text = "ratio=${"%.3f".format(motionRatio)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    lastFile?.let {
                        Text(
                            text = "Last file: ${it.substringAfterLast('/')}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = { if (isMonitoring) onStopMonitoring() else onStartMonitoring() }
            ) {
                Text(if (isMonitoring) "Stop Monitoring" else "Start Monitoring (-1 koin)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    onClick = onToggleCamera
                ) {
                    Text("Switch camera")
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    onClick = { if (isBackgroundMonitoring) onStopBackground() else onStartBackground() }
                ) {
                    Text(if (isBackgroundMonitoring) "Stop BG" else "Start In BG")
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
            if (showAdConfirm) {
                AlertDialog(
                    onDismissRequest = { showAdConfirm = false },
                    title = { Text("Tambah koin") },
                    text = {
                        Text(
                            if (adReady) {
                                "Tonton iklan untuk mendapatkan 5 koin?"
                            } else if (adLoading) {
                                "Iklan sedang dimuat. Tunggu sebentar."
                            } else {
                                "Iklan belum siap. Muat ulang iklan?"
                            }
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showAdConfirm = false
                                if (adReady && !adLoading) {
                                    onWatchAd()
                                } else if (!adLoading) {
                                    onRefreshAd()
                                }
                            }
                        ) {
                            Text(
                                when {
                                    adReady -> "Tonton iklan"
                                    adLoading -> "Oke"
                                    else -> "Muat ulang"
                                }
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAdConfirm = false }) { Text("Batal") }
                    }
                )
            }
        }
    }
}

@Composable
private fun CoinBadge(
    coins: Int,
    adReady: Boolean,
    adLoading: Boolean,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x22FFFFFF)
        )
    ) {
        val addTint = when {
            adLoading -> Color(0xFF8A8A8A)
            adReady -> Color(0xFF7CFF6B)
            else -> Color(0xFFB0B0B0)
        }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MonetizationOn,
                contentDescription = "Koin",
                tint = Color(0xFFFFD54F)
            )
            Text(
                text = "Koin: $coins",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = "Tambah koin",
                    tint = addTint
                )
            }
        }
    }
}

@Composable
private fun TimeChip(label: String, timeText: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .background(Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("$label: $timeText", style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

private fun formatMinutes(totalMinutes: Int): String {
    val minutes = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
    val hour = minutes / 60
    val minute = minutes % 60
    return "%02d:%02d".format(hour, minute)
}
