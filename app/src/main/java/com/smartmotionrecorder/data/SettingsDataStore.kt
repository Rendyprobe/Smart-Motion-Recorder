package com.smartmotionrecorder.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "monitor_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val motionRatioThreshold = floatPreferencesKey("motion_ratio_threshold")
        val pixelDiffThreshold = intPreferencesKey("pixel_diff_threshold")
        val consecutiveMotionFrames = intPreferencesKey("consecutive_motion_frames")
        val stopDelayMs = intPreferencesKey("stop_delay_ms")
        val cooldownMs = intPreferencesKey("cooldown_ms")
        val analyzeEveryNthFrame = intPreferencesKey("analyze_every_nth_frame")
        val downsampleStride = intPreferencesKey("downsample_stride")
        val recordAudio = booleanPreferencesKey("record_audio")
        val useBackCamera = booleanPreferencesKey("use_back_camera")
        val recordingMode = intPreferencesKey("recording_mode")
        val storageMode = intPreferencesKey("storage_mode")
        val storageFolderName = stringPreferencesKey("storage_folder_name")
        val uploadToDrive = booleanPreferencesKey("upload_to_drive")
        val uploadWifiOnly = booleanPreferencesKey("upload_wifi_only")
        val driveAccount = stringPreferencesKey("drive_account")
        val safFolderUri = stringPreferencesKey("saf_folder_uri")
        val scheduleEnabled = booleanPreferencesKey("schedule_enabled")
        val scheduleStartMinutes = intPreferencesKey("schedule_start_minutes")
        val scheduleEndMinutes = intPreferencesKey("schedule_end_minutes")
        val scheduleBackgroundOnly = booleanPreferencesKey("schedule_background_only")
        val torchUiEnabled = booleanPreferencesKey("torch_ui_enabled")
        val autoExposureLock = booleanPreferencesKey("auto_exposure_lock")
        val remoteControlEnabled = booleanPreferencesKey("remote_control_enabled")
        val remoteControlPort = intPreferencesKey("remote_control_port")
        val remoteControlPin = stringPreferencesKey("remote_control_pin")
        val antiTamperEnabled = booleanPreferencesKey("anti_tamper_enabled")
    }

    val settingsFlow: Flow<MonitoringSettings> = context.settingsDataStore.data.map { prefs ->
        MonitoringSettings(
            motionRatioThreshold = prefs[Keys.motionRatioThreshold] ?: MonitoringSettings().motionRatioThreshold,
            pixelDiffThreshold = prefs[Keys.pixelDiffThreshold] ?: MonitoringSettings().pixelDiffThreshold,
            consecutiveMotionFrames = prefs[Keys.consecutiveMotionFrames] ?: MonitoringSettings().consecutiveMotionFrames,
            stopDelayMs = prefs[Keys.stopDelayMs] ?: MonitoringSettings().stopDelayMs,
            cooldownMs = prefs[Keys.cooldownMs] ?: MonitoringSettings().cooldownMs,
            analyzeEveryNthFrame = prefs[Keys.analyzeEveryNthFrame] ?: MonitoringSettings().analyzeEveryNthFrame,
            downsampleStride = prefs[Keys.downsampleStride] ?: MonitoringSettings().downsampleStride,
            recordAudio = prefs[Keys.recordAudio] ?: MonitoringSettings().recordAudio,
            useBackCamera = prefs[Keys.useBackCamera] ?: MonitoringSettings().useBackCamera,
            recordingMode = prefs[Keys.recordingMode]?.let { safeMode(it) } ?: MonitoringSettings().recordingMode,
            storageMode = prefs[Keys.storageMode]?.let { safeStorage(it) } ?: MonitoringSettings().storageMode,
            storageFolderName = prefs[Keys.storageFolderName] ?: MonitoringSettings().storageFolderName,
            uploadToDrive = prefs[Keys.uploadToDrive] ?: MonitoringSettings().uploadToDrive,
            uploadWifiOnly = prefs[Keys.uploadWifiOnly] ?: MonitoringSettings().uploadWifiOnly,
            driveAccount = prefs[Keys.driveAccount] ?: MonitoringSettings().driveAccount,
            safFolderUri = prefs[Keys.safFolderUri] ?: MonitoringSettings().safFolderUri,
            scheduleEnabled = prefs[Keys.scheduleEnabled] ?: MonitoringSettings().scheduleEnabled,
            scheduleStartMinutes = prefs[Keys.scheduleStartMinutes] ?: MonitoringSettings().scheduleStartMinutes,
            scheduleEndMinutes = prefs[Keys.scheduleEndMinutes] ?: MonitoringSettings().scheduleEndMinutes,
            scheduleBackgroundOnly = prefs[Keys.scheduleBackgroundOnly] ?: MonitoringSettings().scheduleBackgroundOnly,
            torchUiEnabled = prefs[Keys.torchUiEnabled] ?: MonitoringSettings().torchUiEnabled,
            autoExposureLock = prefs[Keys.autoExposureLock] ?: MonitoringSettings().autoExposureLock,
            remoteControlEnabled = prefs[Keys.remoteControlEnabled] ?: MonitoringSettings().remoteControlEnabled,
            remoteControlPort = prefs[Keys.remoteControlPort] ?: MonitoringSettings().remoteControlPort,
            remoteControlPin = prefs[Keys.remoteControlPin] ?: MonitoringSettings().remoteControlPin,
            antiTamperEnabled = prefs[Keys.antiTamperEnabled] ?: MonitoringSettings().antiTamperEnabled
        )
    }

    suspend fun updateSettings(transform: (MonitoringSettings) -> MonitoringSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = fromPreferences(prefs)
            val updated = transform(current)
            prefs[Keys.motionRatioThreshold] = updated.motionRatioThreshold
            prefs[Keys.pixelDiffThreshold] = updated.pixelDiffThreshold
            prefs[Keys.consecutiveMotionFrames] = updated.consecutiveMotionFrames
            prefs[Keys.stopDelayMs] = updated.stopDelayMs
            prefs[Keys.cooldownMs] = updated.cooldownMs
            prefs[Keys.analyzeEveryNthFrame] = updated.analyzeEveryNthFrame
            prefs[Keys.downsampleStride] = updated.downsampleStride
            prefs[Keys.recordAudio] = updated.recordAudio
            prefs[Keys.useBackCamera] = updated.useBackCamera
            prefs[Keys.recordingMode] = updated.recordingMode.ordinal
            prefs[Keys.storageMode] = updated.storageMode.ordinal
            prefs[Keys.storageFolderName] = updated.storageFolderName
            prefs[Keys.uploadToDrive] = updated.uploadToDrive
            prefs[Keys.uploadWifiOnly] = updated.uploadWifiOnly
            prefs[Keys.driveAccount] = updated.driveAccount
            prefs[Keys.safFolderUri] = updated.safFolderUri
            prefs[Keys.scheduleEnabled] = updated.scheduleEnabled
            prefs[Keys.scheduleStartMinutes] = updated.scheduleStartMinutes
            prefs[Keys.scheduleEndMinutes] = updated.scheduleEndMinutes
            prefs[Keys.scheduleBackgroundOnly] = updated.scheduleBackgroundOnly
            prefs[Keys.torchUiEnabled] = updated.torchUiEnabled
            prefs[Keys.autoExposureLock] = updated.autoExposureLock
            prefs[Keys.remoteControlEnabled] = updated.remoteControlEnabled
            prefs[Keys.remoteControlPort] = updated.remoteControlPort
            prefs[Keys.remoteControlPin] = updated.remoteControlPin
            prefs[Keys.antiTamperEnabled] = updated.antiTamperEnabled
        }
    }

    suspend fun setSettings(settings: MonitoringSettings) {
        updateSettings { settings }
    }

    private fun fromPreferences(prefs: Preferences): MonitoringSettings = MonitoringSettings(
        motionRatioThreshold = prefs[Keys.motionRatioThreshold] ?: MonitoringSettings().motionRatioThreshold,
        pixelDiffThreshold = prefs[Keys.pixelDiffThreshold] ?: MonitoringSettings().pixelDiffThreshold,
        consecutiveMotionFrames = prefs[Keys.consecutiveMotionFrames] ?: MonitoringSettings().consecutiveMotionFrames,
        stopDelayMs = prefs[Keys.stopDelayMs] ?: MonitoringSettings().stopDelayMs,
        cooldownMs = prefs[Keys.cooldownMs] ?: MonitoringSettings().cooldownMs,
        analyzeEveryNthFrame = prefs[Keys.analyzeEveryNthFrame] ?: MonitoringSettings().analyzeEveryNthFrame,
        downsampleStride = prefs[Keys.downsampleStride] ?: MonitoringSettings().downsampleStride,
        recordAudio = prefs[Keys.recordAudio] ?: MonitoringSettings().recordAudio,
        useBackCamera = prefs[Keys.useBackCamera] ?: MonitoringSettings().useBackCamera,
        recordingMode = prefs[Keys.recordingMode]?.let { safeMode(it) } ?: MonitoringSettings().recordingMode,
        storageMode = prefs[Keys.storageMode]?.let { safeStorage(it) } ?: MonitoringSettings().storageMode,
        storageFolderName = prefs[Keys.storageFolderName] ?: MonitoringSettings().storageFolderName,
        scheduleEnabled = prefs[Keys.scheduleEnabled] ?: MonitoringSettings().scheduleEnabled,
        scheduleStartMinutes = prefs[Keys.scheduleStartMinutes] ?: MonitoringSettings().scheduleStartMinutes,
        scheduleEndMinutes = prefs[Keys.scheduleEndMinutes] ?: MonitoringSettings().scheduleEndMinutes,
        scheduleBackgroundOnly = prefs[Keys.scheduleBackgroundOnly] ?: MonitoringSettings().scheduleBackgroundOnly,
        torchUiEnabled = prefs[Keys.torchUiEnabled] ?: MonitoringSettings().torchUiEnabled,
        autoExposureLock = prefs[Keys.autoExposureLock] ?: MonitoringSettings().autoExposureLock,
        remoteControlEnabled = prefs[Keys.remoteControlEnabled] ?: MonitoringSettings().remoteControlEnabled,
        remoteControlPort = prefs[Keys.remoteControlPort] ?: MonitoringSettings().remoteControlPort,
        remoteControlPin = prefs[Keys.remoteControlPin] ?: MonitoringSettings().remoteControlPin,
        antiTamperEnabled = prefs[Keys.antiTamperEnabled] ?: MonitoringSettings().antiTamperEnabled
    )

    private fun safeMode(ordinal: Int): RecordingMode {
        val values = RecordingMode.values()
        return values.getOrElse(ordinal) { MonitoringSettings().recordingMode }
    }

    private fun safeStorage(ordinal: Int): StorageMode {
        val values = StorageMode.values()
        return values.getOrElse(ordinal) { MonitoringSettings().storageMode }
    }
}
