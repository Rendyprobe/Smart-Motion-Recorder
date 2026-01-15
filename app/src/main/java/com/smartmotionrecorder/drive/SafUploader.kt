package com.smartmotionrecorder.drive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object SafUploader {
    private const val KEY_FILE_PATH = "file_path"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_WIFI_ONLY = "wifi_only"

    fun enqueueCopy(context: Context, filePath: String, treeUri: String, wifiOnly: Boolean) {
        if (treeUri.isBlank()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val data = Data.Builder()
            .putString(KEY_FILE_PATH, filePath)
            .putString(KEY_TREE_URI, treeUri)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .build()

        val req = OneTimeWorkRequestBuilder<SafUploadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueue(req)
    }

    internal fun filePathKey() = KEY_FILE_PATH
    internal fun treeUriKey() = KEY_TREE_URI
}
