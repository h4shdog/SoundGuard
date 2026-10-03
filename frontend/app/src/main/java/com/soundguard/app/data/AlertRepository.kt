package com.soundguard.app.data

import androidx.compose.runtime.mutableStateListOf
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
    var current: DetectionResult? = null
        private set

    // ── Service running state ─────────────────────────────────
    var isRunning: Boolean = false
        private set

    private var nextId = 1

    // ─────────────────────────────────────────────────────────
    fun setRunning(running: Boolean) {
        isRunning = running
        if (!running) current = null
    }

    fun postDetection(result: DetectionResult) {
        current = result

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
        current  = null
        isRunning = false
    }

    private fun formatNow(): String {
        return SimpleDateFormat("MMM dd  HH:mm", Locale.getDefault()).format(Date())
    }
}
