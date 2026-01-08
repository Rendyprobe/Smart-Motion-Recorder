package com.gathilekha.motionrecorder.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val logFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun fileTimestamp(): String = fileFormat.format(Date())

    fun nowTimeString(): String = logFormat.format(Date())
}
