package com.example.android_screen_relay

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    data class LogEntry(
        val timestamp: Long,
        val message: String,
        val type: LogType
    )

    enum class LogType {
        INFO, ERROR, OUTGOING, INCOMING
    }

    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addLog(message: String, type: LogType = LogType.INFO) {
        synchronized(_logs) {
            if (_logs.size > 1000) {
                _logs.removeAt(0)
            }
            _logs.add(LogEntry(System.currentTimeMillis(), message, type))
        }
    }
    
    fun clearLogs() {
        _logs.clear()
    }

    fun getFormattedTimestamp(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
}
