package com.soundguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.soundguard.app.MainActivity
import com.soundguard.app.R
import com.soundguard.app.data.AlertRepository
import com.soundguard.app.data.DetectionResult
import com.soundguard.app.data.ModelType
import com.soundguard.app.data.SoundClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps audio monitoring alive when the user
 * leaves the app.  The service posts detections to [AlertRepository]
 * which the UI observes reactively.
 *
 * Replace the [runDemoLoop] body with real AudioRecord + TFLite
 * inference once BestModel.tflite is integrated.
 */
class SoundMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.soundguard.app.START"
        const val ACTION_STOP  = "com.soundguard.app.STOP"

        private const val CHANNEL_ID   = "soundguard_monitor"
        private const val NOTIF_ID     = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectionJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP  -> stopMonitoring()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        AlertRepository.setRunning(false)
        super.onDestroy()
    }

    // ── Start / stop ──────────────────────────────────────────

    private fun startMonitoring() {
        AlertRepository.setRunning(true)
        startForeground(NOTIF_ID, buildNotification("Listening for emergency sounds…"))
        detectionJob = scope.launch { runDemoLoop() }
    }

    private fun stopMonitoring() {
        detectionJob?.cancel()
        AlertRepository.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Demo inference loop ───────────────────────────────────
    // Replace this with real AudioRecord + TFLite inference.
    private suspend fun runDemoLoop() {
        val model = ModelType.BEST_MODEL
        val demos = listOf(
            DetectionResult(SoundClass.BACKGROUND, 0.89f, 9,  model, false),
            DetectionResult(SoundClass.BACKGROUND, 0.92f, 8,  model, false),
            DetectionResult(SoundClass.SIREN,      0.92f, 10, model, true),
            DetectionResult(SoundClass.BACKGROUND, 0.91f, 9,  model, false),
            DetectionResult(SoundClass.FIRE_ALARM, 0.97f, 12, model, true),
            DetectionResult(SoundClass.BACKGROUND, 0.88f, 8,  model, false),
        )
        var i = 0
        while (true) {
            val result = demos[i % demos.size]
            AlertRepository.postDetection(result)

            // Update notification text when an emergency is detected
            if (result.isEmergency) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification("⚠️ ${result.soundClass.label} detected!"))
            }

            i++
            delay(2000)
        }
    }

    // ── Notification ──────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SoundGuard Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing audio monitoring notification"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SoundGuard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
