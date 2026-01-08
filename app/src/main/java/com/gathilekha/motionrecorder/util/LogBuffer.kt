package com.gathilekha.motionrecorder.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LogBuffer(private val capacity: Int = 100) {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun add(message: String) {
        val timestamped = "${TimeUtil.nowTimeString()} - $message"
        _logs.update { current ->
            (current + timestamped).takeLast(capacity)
        }
    }
}
