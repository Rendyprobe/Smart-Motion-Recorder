package com.smartmotionrecorder.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.smartmotionrecorder.util.SnapshotRepository
import com.smartmotionrecorder.util.SnapshotUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SnapshotCameraController(
    private val context: android.content.Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var bound = false
    private var lastUseBack = true
    private var lastSnapshotMs: Long = 0L

    suspend fun start(useBackCamera: Boolean) {
        if (bound && lastUseBack == useBackCamera) return
        lastUseBack = useBackCamera
        val provider = getCameraProvider()
        withContext(Dispatchers.Main) {
            cameraProvider = provider
            provider.unbindAll()
            val selector = if (useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { created ->
                    created.setAnalyzer(analysisExecutor) { image ->
                        analyze(image)
                    }
                }
            imageAnalysis = analysis
            provider.bindToLifecycle(lifecycleOwner, selector, analysis)
            bound = true
        }
    }

    fun stop() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        bound = false
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastSnapshotMs < 1000L) return
            val jpeg = SnapshotUtil.imageToJpeg(image, quality = 60) ?: return
            SnapshotRepository.update(jpeg)
            lastSnapshotMs = now
        } finally {
            image.close()
        }
    }

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
}
