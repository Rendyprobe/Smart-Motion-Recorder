package com.smartmotionrecorder.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Camera
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
import com.smartmotionrecorder.data.MonitoringSettings
import com.smartmotionrecorder.motion.MotionDetector
import com.smartmotionrecorder.motion.MotionResult
import com.smartmotionrecorder.recording.RecordingManager
import com.smartmotionrecorder.util.SnapshotRepository
import com.smartmotionrecorder.util.SnapshotUtil
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.SurfaceRequest
import android.graphics.SurfaceTexture
import android.view.Surface

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
    private var monitoringEnabled: Boolean = false
    private var camera: Camera? = null
    private var lastAeLock: Boolean? = null
    private var lastSnapshotMs: Long = 0L
    private val mainExecutor by lazy { ContextCompat.getMainExecutor(context) }
    private var dummySurfaceTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null

    suspend fun start(
        previewProvider: Preview.SurfaceProvider?,
        useBackCamera: Boolean,
        recordAudio: Boolean,
        settings: MonitoringSettings,
        monitoringEnabled: Boolean
    ) {
        this.recordAudio = recordAudio
        this.currentSettings = settings
        if (this.monitoringEnabled != monitoringEnabled) {
            setMonitoringEnabled(monitoringEnabled)
        }
        if (bound && lastSurfaceProvider === previewProvider && lastBindUseBack == useBackCamera) {
            motionDetector.updateSettings(currentSettings)
            applyAutoExposureLock()
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

                val surfaceProvider = previewProvider ?: buildDummySurfaceProvider()
                preview = Preview.Builder().build().apply {
                    setSurfaceProvider(surfaceProvider)
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
                camera = this@MotionCameraController.cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    *useCases.toTypedArray()
                )
                applyAutoExposureLock()
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
        monitoringEnabled = false
        camera = null
        lastAeLock = null
        releaseDummySurface()
    }

    private fun analyze(image: ImageProxy) {
        maybeCaptureSnapshot(image)
        if (!monitoringEnabled) {
            image.close()
            return
        }
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

    fun setMonitoringEnabled(enabled: Boolean) {
        if (monitoringEnabled == enabled) return
        monitoringEnabled = enabled
        motionDetector.reset()
        if (!enabled && isRecording) {
            stopRecording("Monitoring disabled")
        }
        applyAutoExposureLock()
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

    fun hasFlashUnit(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    fun setTorchEnabled(enabled: Boolean): Boolean {
        val currentCamera = camera ?: return false
        if (!currentCamera.cameraInfo.hasFlashUnit()) return false
        currentCamera.cameraControl.enableTorch(enabled)
        return true
    }

    private fun maybeCaptureSnapshot(image: ImageProxy) {
        if (!currentSettings.remoteControlEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastSnapshotMs < 1000L) return
        val jpeg = SnapshotUtil.imageToJpeg(image, quality = 60) ?: return
        SnapshotRepository.update(jpeg)
        lastSnapshotMs = now
    }

    private fun applyAutoExposureLock() {
        val currentCamera = camera ?: return
        val shouldLock = monitoringEnabled && currentSettings.autoExposureLock
        if (lastAeLock == shouldLock) return
        val supported = Camera2CameraInfo.from(currentCamera.cameraInfo)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
        if (!supported) {
            lastAeLock = shouldLock
            return
        }
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, shouldLock)
            .build()
        Camera2CameraControl.from(currentCamera.cameraControl).setCaptureRequestOptions(options)
        lastAeLock = shouldLock
    }

    private fun buildDummySurfaceProvider(): Preview.SurfaceProvider {
        return Preview.SurfaceProvider { request: SurfaceRequest ->
            val resolution = request.resolution
            val texture = dummySurfaceTexture ?: SurfaceTexture(0).also { dummySurfaceTexture = it }
            texture.setDefaultBufferSize(resolution.width, resolution.height)
            val surface = dummySurface ?: Surface(texture).also { dummySurface = it }
            request.provideSurface(surface, mainExecutor) { _ ->
                // Keep surface until stop() to maintain camera stream
            }
        }
    }

    private fun releaseDummySurface() {
        try {
            dummySurface?.release()
        } catch (_: Exception) {
        }
        try {
            dummySurfaceTexture?.release()
        } catch (_: Exception) {
        }
        dummySurface = null
        dummySurfaceTexture = null
    }
}
