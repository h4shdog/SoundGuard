package com.soundguard.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory singleton that holds the detection history and the current
 * live detection result.  Both the foreground service and the UI read/write
 * through this object so they stay in sync regardless of which screen is
 * open or whether the app is in the foreground.
 */
object AlertRepository {

    // ── Live history (most-recent first) ─────────────────────
    val history = mutableStateListOf<AlertRecord>()

    // ── Current detection (null = idle) ──────────────────────
    private var _current by mutableStateOf<DetectionResult?>(null)
    val current: DetectionResult? get() = _current

    // ── Service running state ─────────────────────────────────
    private var _isRunning by mutableStateOf(false)
    val isRunning: Boolean get() = _isRunning

    private var nextId = 1

    // ─────────────────────────────────────────────────────────
    fun setRunning(running: Boolean) {
        _isRunning = running
        if (!running) _current = null
    }

    fun postDetection(result: DetectionResult) {
        _current = result

        // Only record emergency detections (fire alarm / siren) in history
        if (result.isEmergency) {
            val record = AlertRecord(
                id          = nextId++,
                soundClass  = result.soundClass,
                model       = result.model,
                confidence  = result.confidence,
                inferenceMs = result.inferenceMs,
                timestamp   = formatNow()
            )
            history.add(0, record)   // newest first
        }
    }

    fun clear() {
        history.clear()
        _current  = null
        _isRunning = false
    }

    private fun formatNow(): String {
        return SimpleDateFormat("MMM dd  HH:mm", Locale.getDefault()).format(Date())
    }
}
