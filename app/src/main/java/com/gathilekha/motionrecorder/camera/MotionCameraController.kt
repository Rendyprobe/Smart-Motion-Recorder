package com.gathilekha.motionrecorder.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.gathilekha.motionrecorder.data.MonitoringSettings
import com.gathilekha.motionrecorder.motion.MotionDetector
import com.gathilekha.motionrecorder.motion.MotionResult
import com.gathilekha.motionrecorder.recording.RecordingManager
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi

class MotionCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val callback: Callback
) {
    interface Callback {
        fun onMotion(ratio: Float, avg: Float)
        fun onMotionTrigger()
        fun onRecordingStarted(path: String)
        fun onRecordingStopped(path: String?)
        fun onError(message: String)
    }

    private val motionDetector = MotionDetector()
    private val recordingManager = RecordingManager(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private var recordingPath: String? = null
    private var lastMotion = 0L
    private var isRecording = false
    private var cooldownUntil = 0L
    private var recordAudio: Boolean = false
    private var lastBindUseBack = true
    private var lastSurfaceProvider: Preview.SurfaceProvider? = null
    private var bound = false
    private var currentSettings: MonitoringSettings = MonitoringSettings()

    suspend fun start(
        previewProvider: Preview.SurfaceProvider?,
        useBackCamera: Boolean,
        recordAudio: Boolean,
        settings: MonitoringSettings
    ) {
        this.recordAudio = recordAudio
        this.currentSettings = settings
        if (bound && lastSurfaceProvider === previewProvider && lastBindUseBack == useBackCamera) {
            motionDetector.updateSettings(currentSettings)
            return
        }
        lastSurfaceProvider = previewProvider
        lastBindUseBack = useBackCamera
        try {
            val provider = getCameraProvider()
            withContext(Dispatchers.Main) {
                cameraProvider = provider
                provider.unbindAll()

                val selector = if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

                preview = previewProvider?.let {
                    Preview.Builder().build().apply {
                        setSurfaceProvider(it)
                    }
                }

                imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { analysis ->
                        motionDetector.updateSettings(currentSettings)
                        analysis.setAnalyzer(analysisExecutor) { image ->
                            analyze(image)
                        }
                    }

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val useCases = mutableListOf<androidx.camera.core.UseCase>()
                preview?.let { useCases.add(it) }
                imageAnalysis?.let { useCases.add(it) }
                videoCapture?.let { useCases.add(it) }
                provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    *useCases.toTypedArray()
                )
                bound = true
            }
        } catch (e: Exception) {
            callback.onError("Bind failed: ${e.message}")
            bound = false
        }
    }

    fun stop() {
        cameraProvider?.unbindAll()
        imageAnalysis?.clearAnalyzer()
        recordingManager.stopBlocking()
        isRecording = false
        recordingPath = null
        bound = false
    }

    private fun analyze(image: ImageProxy) {
        val result: MotionResult = motionDetector.analyze(image)
        callback.onMotion(result.ratio, result.ratioAvg)
        val now = System.currentTimeMillis()
        if (result.motionDetected) {
            lastMotion = now
            if (!isRecording && now >= cooldownUntil) {
                callback.onMotionTrigger()
                startRecording()
            }
        } else {
            if (isRecording && now - lastMotion > currentSettings.stopDelayMs) {
                stopRecording("No motion")
            }
        }
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        if (isRecording) return
        isRecording = true
        val enableAudio = recordAudio && hasAudioPermission()
        recordingPath = recordingManager.startBlocking(
            videoCapture = capture,
            recordAudio = enableAudio,
            storageMode = currentSettings.storageMode,
            folderName = currentSettings.storageFolderName
        ) { filePath, success, error ->
            if (!success) {
                isRecording = false
                callback.onError(error ?: "Recording failed${if (recordAudio && !enableAudio) " (audio denied -> recording muted)" else ""}")
                // Retry without audio if audio path failed
                if (enableAudio) {
                    val retryPath = recordingManager.startBlocking(
                        videoCapture = capture,
                        recordAudio = false,
                        storageMode = currentSettings.storageMode,
                        folderName = currentSettings.storageFolderName
                    ) { pathRetry, successRetry, errorRetry ->
                        if (!successRetry) {
                            callback.onError(errorRetry ?: "Recording retry failed (mute)")
                        } else {
                            callback.onRecordingStarted(pathRetry)
                        }
                    }
                    if (retryPath != null) {
                        recordingPath = retryPath
                        isRecording = true
                        callback.onRecordingStarted(retryPath)
                    }
                }
            }
        }
        recordingPath?.let { callback.onRecordingStarted(it) }
        lastMotion = System.currentTimeMillis()
    }

    private fun stopRecording(reason: String) {
        val path = recordingPath
        recordingManager.stopBlocking()
        isRecording = false
        recordingPath = null
        cooldownUntil = System.currentTimeMillis() + currentSettings.cooldownMs
        callback.onRecordingStopped(path)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get()) {}
                } catch (ex: Exception) {
                    cont.cancel(ex)
                }
            }, ContextCompat.getMainExecutor(context))
        }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
}
