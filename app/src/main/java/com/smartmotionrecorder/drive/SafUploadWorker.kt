package com.smartmotionrecorder.drive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SafUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filePath = inputData.getString(SafUploader.filePathKey()) ?: return@withContext Result.failure()
        val treeUriString = inputData.getString(SafUploader.treeUriKey()) ?: return@withContext Result.failure()
        val treeUri = Uri.parse(treeUriString)
        val src = File(filePath)
        if (!src.exists()) return@withContext Result.failure()

        try {
            val tree = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return@withContext Result.failure()
            val dest = tree.createFile("video/mp4", src.name) ?: return@withContext Result.retry()
            applicationContext.contentResolver.openOutputStream(dest.uri)?.use { out ->
                src.inputStream().use { input -> input.copyTo(out) }
            } ?: return@withContext Result.retry()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
