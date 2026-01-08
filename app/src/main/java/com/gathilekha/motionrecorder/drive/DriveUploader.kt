package com.gathilekha.motionrecorder.drive

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object DriveUploader {
    private const val KEY_FILE_PATH = "file_path"
    private const val KEY_ACCOUNT = "account_name"
    private const val KEY_WIFI_ONLY = "wifi_only"

    fun enqueueUpload(context: Context, filePath: String, accountName: String?, wifiOnly: Boolean) {
        if (accountName.isNullOrBlank()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(KEY_FILE_PATH, filePath)
            .putString(KEY_ACCOUNT, accountName)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .build()

        val request = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    internal fun filePathKey() = KEY_FILE_PATH
    internal fun accountKey() = KEY_ACCOUNT
}
