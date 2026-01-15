package com.smartmotionrecorder.service

import com.smartmotionrecorder.data.MonitoringSettings

object ServiceConfig {
    var settings: MonitoringSettings = MonitoringSettings()
    var lastSavedFile: String? = null
}
