package com.soundguard.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-memory singleton shared between the foreground service and the UI.
 *
 * All Compose snapshot-state writes are dispatched to the main thread so
 * observers are always notified on the correct thread, regardless of which
 * coroutine dispatcher the service is running on.
 */
object AlertRepository {

    // ── Live detection history (most-recent first) ────────────
    val history = mutableStateListOf<AlertRecord>()

    // ── Current live detection result (null = idle / not started) ─
    private var _current by mutableStateOf<DetectionResult?>(null)
    val current: DetectionResult? get() = _current

    // ── Service running flag ──────────────────────────────────
    private var _isRunning by mutableStateOf(false)
    val isRunning: Boolean get() = _isRunning

    // ── Error message (null = no error) ──────────────────────
    private var _errorMessage by mutableStateOf<String?>(null)
    val errorMessage: String? get() = _errorMessage

    private var nextId = 1
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ─────────────────────────────────────────────────────────

    fun setRunning(running: Boolean) {
        mainHandler.post {
            _isRunning = running
            if (!running) {
                _current = null
            }
        }
    }

    /**
     * Called by the service after every inference window.
     * Only fire alarm and siren detections are persisted to [history].
     * [audioPath] is the absolute path to the saved WAV clip (may be null).
     */
    fun postDetection(result: DetectionResult, audioPath: String? = null) {
        mainHandler.post {
            _current      = result
            _errorMessage = null   // clear any previous error on successful inference

            // Record emergency detections (fire alarm / siren) in history
            if (result.isEmergency) {
                history.add(
                    0,
                    AlertRecord(
                        id          = nextId++,
                        soundClass  = result.soundClass,
                        model       = result.model,
                        confidence  = result.confidence,
                        inferenceMs = result.inferenceMs,
                        timestamp   = formatNow(),
                        audioPath   = audioPath
                    )
                )
            }
        }
    }

    /**
     * Called by the service when a non-recoverable error occurs
     * (e.g. model failed to load, AudioRecord not initialized).
     * Pass null to clear a previous error message.
     */
    fun postError(message: String?) {
        mainHandler.post {
            _errorMessage = message
            if (message != null) {
                _isRunning = false
                _current   = null
            }
        }
    }

    fun clear() {
        mainHandler.post {
            history.clear()
            _current      = null
            _isRunning    = false
            _errorMessage = null
        }
    }

    /** Delete every record from history and their associated audio clips. */
    fun deleteAllHistory() {
        mainHandler.post {
            history.forEach { it.audioPath?.let { p -> java.io.File(p).delete() } }
            history.clear()
        }
    }

    /** Delete a single record by its id and its associated audio clip. */
    fun deleteRecord(id: Int) {
        mainHandler.post {
            history.find { it.id == id }?.audioPath?.let { java.io.File(it).delete() }
            history.removeAll { it.id == id }
        }
    }

    private fun formatNow(): String =
        SimpleDateFormat("MMM dd  HH:mm", Locale.getDefault()).format(Date())
}
