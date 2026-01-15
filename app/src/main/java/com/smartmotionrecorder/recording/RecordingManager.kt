package com.smartmotionrecorder.recording

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.Recorder
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.smartmotionrecorder.data.StorageMode
import com.smartmotionrecorder.util.TimeUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class RecordingManager(private val context: Context) {
    private val mutex = Mutex()
    private var activeRecording: Recording? = null

    suspend fun startRecording(
        videoCapture: VideoCapture<Recorder>,
        recordAudio: Boolean,
        storageMode: StorageMode,
        folderName: String,
        onFinalize: (String, Boolean, String?) -> Unit
    ): String? = mutex.withLock {
        if (activeRecording != null) return@withLock null
        return@withLock startInternal(videoCapture, recordAudio, storageMode, folderName, onFinalize)
    }

    suspend fun stopRecording(): String? = mutex.withLock {
        val recording = activeRecording ?: return@withLock null
        recording.stop()
        activeRecording = null
        return@withLock null
    }

    fun startBlocking(
        videoCapture: VideoCapture<Recorder>,
        recordAudio: Boolean,
        storageMode: StorageMode,
        folderName: String,
        onFinalize: (String, Boolean, String?) -> Unit
    ): String? {
        if (activeRecording != null) return null
        return startInternal(videoCapture, recordAudio, storageMode, folderName, onFinalize)
    }

    fun stopBlocking(): String? {
        val recording = activeRecording ?: return null
        recording.stop()
        activeRecording = null
        return null
    }

    private fun startInternal(
        videoCapture: VideoCapture<Recorder>,
        recordAudio: Boolean,
        storageMode: StorageMode,
        folderName: String,
        onFinalize: (String, Boolean, String?) -> Unit
    ): String? {
        val safeFolder = folderName.ifBlank { "MotionRecorder" }
        val fileName = "motion_${TimeUtil.fileTimestamp()}.mp4"

        val useMediaStore = storageMode == StorageMode.PUBLIC_MEDIASTORE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pending: PendingRecording
        var displayPath = ""

        if (useMediaStore) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$safeFolder")
            }
            val outputOptions = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()
            pending = videoCapture.output.prepareRecording(context, outputOptions)
            displayPath = "${Environment.DIRECTORY_MOVIES}/$safeFolder/$fileName"
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                safeFolder
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, fileName)
            val outputOptions = FileOutputOptions.Builder(file).build()
            pending = videoCapture.output.prepareRecording(context, outputOptions)
            displayPath = file.absolutePath
        }

        val pendingRecording = pending.apply {
            if (recordAudio && hasRecordAudioPermission()) {
                withAudioEnabled()
            }
        }

        val recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
                val success = event.error == VideoRecordEvent.Finalize.ERROR_NONE
                val finalPath = event.outputResults.outputUri?.toString() ?: displayPath
                val errorMessage = if (success) null else event.cause?.message
                onFinalize(finalPath, success, errorMessage)
            }
        }
        activeRecording = recording
        return displayPath
    }

    private fun hasRecordAudioPermission(): Boolean {
        return PermissionChecker.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PermissionChecker.PERMISSION_GRANTED
    }
}
