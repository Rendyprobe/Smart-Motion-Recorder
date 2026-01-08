package com.gathilekha.motionrecorder.motion

import androidx.camera.core.ImageProxy
import com.gathilekha.motionrecorder.data.MonitoringSettings
import kotlin.math.abs
import kotlin.math.min

data class MotionResult(
    val ratio: Float,
    val ratioAvg: Float,
    val motionDetected: Boolean
)

class MotionDetector {
    private var previousSample: ByteArray? = null
    private val ratioWindow: ArrayDeque<Float> = ArrayDeque()
    private var frameCounter = 0
    private var motionConsecutive = 0
    private var settings: MonitoringSettings = MonitoringSettings()
    private var spikeGuardCount = 0
    private var warmupFrames = DEFAULT_WARMUP_FRAMES

    fun updateSettings(newSettings: MonitoringSettings) {
        settings = newSettings
    }

    fun reset() {
        previousSample = null
        ratioWindow.clear()
        motionConsecutive = 0
        frameCounter = 0
        warmupFrames = DEFAULT_WARMUP_FRAMES
    }

    fun analyze(image: ImageProxy): MotionResult {
        try {
            frameCounter++
            if (frameCounter % settings.analyzeEveryNthFrame != 0) {
                return MotionResult(ratio = 0f, ratioAvg = currentAverage(), motionDetected = false)
            }

            val yPlane = image.planes.firstOrNull() ?: return MotionResult(0f, currentAverage(), false)
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val width = image.width
            val height = image.height
            val stride = settings.downsampleStride.coerceAtLeast(1)

            val samplesPerRow = (width + stride - 1) / stride
            val samplesPerCol = (height + stride - 1) / stride
            val sampleCount = samplesPerRow * samplesPerCol

            if (sampleCount <= 0) {
                return MotionResult(0f, currentAverage(), false)
            }

            val currentSample = ByteArray(sampleCount)
            val prev = previousSample
            var changed = 0
            var total = 0
            var index = 0
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val bufferIndex = y * rowStride + x * pixelStride
                    val value = yPlane.buffer.get(bufferIndex).toInt() and 0xFF
                    currentSample[index] = value.toByte()
                    if (prev != null) {
                        val diff = abs(value - (prev[index].toInt() and 0xFF))
                        if (diff > settings.pixelDiffThreshold) {
                            changed++
                        }
                        total++
                    }
                    index++
                    x += stride
                }
                y += stride
            }

            previousSample = currentSample

            if (prev == null || total == 0) {
                return MotionResult(0f, currentAverage(), false)
            }

            var ratio = changed.toFloat() / total.toFloat()
            if (ratio > 0.6f) {
                spikeGuardCount += 1
            } else {
                spikeGuardCount = 0
            }
            ratio = if (ratio > 0.6f && spikeGuardCount < 2) {
                0f
            } else {
                min(ratio, 0.6f)
            }

            addRatio(ratio)
            val ratioAvg = currentAverage()

            if (ratioAvg >= settings.motionRatioThreshold) {
                motionConsecutive += 1
            } else {
            motionConsecutive = 0
        }
        val triggered = motionConsecutive >= settings.consecutiveMotionFrames && warmupFrames <= 0

        if (warmupFrames > 0) {
            warmupFrames--
        }

        return MotionResult(ratio = ratio, ratioAvg = ratioAvg, motionDetected = triggered)
    } finally {
        image.close()
    }
    }

    private fun addRatio(ratio: Float) {
        ratioWindow.addLast(ratio)
        if (ratioWindow.size > 3) {
            ratioWindow.removeFirst()
        }
    }

    private fun currentAverage(): Float {
        if (ratioWindow.isEmpty()) return 0f
        return ratioWindow.sum() / ratioWindow.size
    }

    companion object {
        private const val DEFAULT_WARMUP_FRAMES = 20
    }
}
