package com.smartmotionrecorder.util

object SnapshotRepository {
    @Volatile
    private var lastJpeg: ByteArray? = null
    @Volatile
    private var lastUpdatedMs: Long = 0L

    fun update(bytes: ByteArray) {
        lastJpeg = bytes
        lastUpdatedMs = System.currentTimeMillis()
    }

    fun get(): ByteArray? = lastJpeg

    fun ageMs(): Long {
        val now = System.currentTimeMillis()
        return if (lastUpdatedMs == 0L) Long.MAX_VALUE else now - lastUpdatedMs
    }
}
