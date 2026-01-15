package com.smartmotionrecorder.data

data class MonitoringSettings(
    val motionRatioThreshold: Float = 0.03f,
    val pixelDiffThreshold: Int = 25,
    val consecutiveMotionFrames: Int = 2,
    val stopDelayMs: Int = 60_000,
    val cooldownMs: Int = 2000,
    val analyzeEveryNthFrame: Int = 2,
    val downsampleStride: Int = 4,
    val recordAudio: Boolean = true,
    val useBackCamera: Boolean = true,
    val recordingMode: RecordingMode = RecordingMode.MOTION_ONLY,
    val storageMode: StorageMode = StorageMode.APP_PRIVATE,
    val storageFolderName: String = "MotionRecorder",
    val uploadToDrive: Boolean = false,
    val uploadWifiOnly: Boolean = true,
    val driveAccount: String = "",
    val safFolderUri: String = "",
    val scheduleEnabled: Boolean = false,
    val scheduleStartMinutes: Int = 22 * 60,
    val scheduleEndMinutes: Int = 6 * 60,
    val scheduleBackgroundOnly: Boolean = false,
    val torchUiEnabled: Boolean = true,
    val autoExposureLock: Boolean = true,
    val remoteControlEnabled: Boolean = false,
    val remoteControlPort: Int = 8080,
    val remoteControlPin: String = "",
    val antiTamperEnabled: Boolean = true
)

enum class MonitoringStatus {
    IDLE,
    MONITORING,
    RECORDING
}

enum class RecordingMode {
    MOTION_ONLY,
    MOTION_10_MIN
}

enum class StorageMode {
    APP_PRIVATE, // app-specific external files dir
    PUBLIC_MEDIASTORE // public Movies via MediaStore (API 29+)
}

data class ServiceState(
    val status: MonitoringStatus = MonitoringStatus.IDLE,
    val motionRatio: Float = 0f,
    val motionRatioAvg: Float = 0f,
    val lastSavedFile: String? = null,
    val message: String? = null
)
