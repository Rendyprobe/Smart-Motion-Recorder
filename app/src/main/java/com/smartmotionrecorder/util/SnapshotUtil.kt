package com.smartmotionrecorder.util

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object SnapshotUtil {
    fun imageToJpeg(image: ImageProxy, quality: Int = 60): ByteArray? {
        return try {
            val nv21 = yuv420ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val nv21 = ByteArray(width * height + (width * height / 2))
        var pos = 0
        var row = 0
        while (row < height) {
            var col = 0
            val yRowStart = row * yRowStride
            while (col < width) {
                nv21[pos++] = yBuffer.get(yRowStart + col * yPixelStride)
                col++
            }
            row++
        }

        val uvHeight = height / 2
        val uvWidth = width / 2
        row = 0
        while (row < uvHeight) {
            var col = 0
            val uRowStart = row * uvRowStride
            val vRowStart = row * vRowStride
            while (col < uvWidth) {
                val uIndex = uRowStart + col * uvPixelStride
                val vIndex = vRowStart + col * vPixelStride
                nv21[pos++] = vBuffer.get(vIndex)
                nv21[pos++] = uBuffer.get(uIndex)
                col++
            }
            row++
        }
        return nv21
    }
}
