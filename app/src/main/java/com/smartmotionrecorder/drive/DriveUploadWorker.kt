package com.smartmotionrecorder.drive

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class DriveUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val path = inputData.getString(DriveUploader.filePathKey()) ?: return@withContext Result.failure()
        val accountName = inputData.getString(DriveUploader.accountKey()) ?: return@withContext Result.failure()
        val file = File(path)
        if (!file.exists()) return@withContext Result.failure()

        val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
        val token: String = try {
            GoogleAuthUtil.getToken(applicationContext, Account(accountName, "com.google"), scope)
        } catch (e: IOException) {
            return@withContext Result.retry()
        } catch (e: GoogleAuthException) {
            return@withContext Result.failure()
        }

        val metadataJson = """{"name":"${file.name}"}"""

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                "metadata",
                metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("video/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,webViewLink")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        return@withContext try {
            client.newCall(request).execute().use { resp ->
                when {
                    resp.isSuccessful -> Result.success()
                    resp.code == 401 -> Result.failure() // token invalid; requires sign-in ulang
                    else -> Result.retry()
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
