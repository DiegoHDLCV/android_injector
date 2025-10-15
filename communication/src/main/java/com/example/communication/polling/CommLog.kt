package com.example.communication.polling

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class CommLogEntry(
    val timestampMs: Long,
    val level: String,
    val tag: String,
    val message: String
)

object CommLog {
    private const val MAX_BUFFER = 500
    private val _entries = MutableStateFlow<List<CommLogEntry>>(emptyList())
    val entries: StateFlow<List<CommLogEntry>> = _entries.asStateFlow()
    private val _events = MutableSharedFlow<CommLogEntry>(extraBufferCapacity = 200)
    val events: SharedFlow<CommLogEntry> = _events.asSharedFlow()

    fun clear() {
        _entries.value = emptyList()
    }

    fun log(level: String, tag: String, message: String) {
        val entry = CommLogEntry(System.currentTimeMillis(), level, tag, message)
        val next = (_entries.value + entry).takeLast(MAX_BUFFER)
        _entries.value = next
        _events.tryEmit(entry)
    }

    fun v(tag: String, message: String) = log("V", tag, message)
    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)
}


