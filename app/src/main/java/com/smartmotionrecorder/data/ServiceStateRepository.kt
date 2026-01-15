package com.smartmotionrecorder.data

import androidx.camera.core.Preview
import com.smartmotionrecorder.util.LogBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ServiceStateRepository {
    private val _serviceState = MutableStateFlow(ServiceState())
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private val logBuffer = LogBuffer()
    val logs = logBuffer.logs

    val previewSurfaceProvider = MutableStateFlow<Preview.SurfaceProvider?>(null)

    fun setStatus(status: MonitoringStatus) {
        _serviceState.update { it.copy(status = status) }
    }

    fun updateMotion(ratio: Float, ratioAvg: Float) {
        _serviceState.update { it.copy(motionRatio = ratio, motionRatioAvg = ratioAvg) }
    }

    fun setLastSavedFile(path: String?) {
        _serviceState.update { it.copy(lastSavedFile = path) }
    }

    fun setMessage(message: String?) {
        _serviceState.update { it.copy(message = message) }
        message?.let { logBuffer.add("Message: $it") }
    }

    fun addLog(message: String) {
        logBuffer.add(message)
    }
}
