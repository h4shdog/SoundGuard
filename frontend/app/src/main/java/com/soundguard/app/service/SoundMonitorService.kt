package com.soundguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.soundguard.app.MainActivity
import com.soundguard.app.data.AlertRepository
import com.soundguard.app.data.DetectionResult
import com.soundguard.app.data.ModelType
import com.soundguard.app.data.SoundClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SoundMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.soundguard.app.START"
        const val ACTION_STOP  = "com.soundguard.app.STOP"

        private const val TAG          = "SoundMonitorService"
        private const val CHANNEL_ID   = "soundguard_monitor"
        private const val NOTIF_ID     = 1
        private const val SAMPLE_RATE  = 16000
        private const val NUM_SAMPLES  = 16000          // 1-second window
        private const val THRESHOLD    = 0.75f

        // Adjust if your model's output order differs — check in Netron
        private val LABEL_ORDER = arrayOf(
            SoundClass.BACKGROUND,
            SoundClass.FIRE_ALARM,
            SoundClass.SIREN
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectionJob: Job? = null
    private var interpreter: Interpreter? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        interpreter = loadModel()
        if (interpreter == null) {
            Log.e(TAG, "BestModel.tflite failed to load — will run mic-only mode")
        } else {
            Log.i(TAG, "BestModel.tflite loaded successfully")
            logModelShapes()
        }
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
        try { interpreter?.close() } catch (_: Exception) {}
        interpreter = null
        AlertRepository.setRunning(false)
        super.onDestroy()
    }

    // ── Start / stop ──────────────────────────────────────

    private fun startMonitoring() {
        if (AlertRepository.isRunning) return   // already running — ignore duplicate start
        AlertRepository.setRunning(true)
        startForeground(NOTIF_ID, buildNotification("Listening for emergency sounds…"))
        detectionJob = scope.launch {
            try {
                if (interpreter != null) runInferenceLoop() else runMicIdleLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Detection loop crashed: ${e.message}", e)
                AlertRepository.setRunning(false)
            }
        }
    }

    private fun stopMonitoring() {
        detectionJob?.cancel()
        AlertRepository.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Load model ────────────────────────────────────────

    private fun loadModel(): Interpreter? {
        return try {
            val afd = assets.openFd("BestModel.tflite")
            val stream = afd.createInputStream()
            val bytes = stream.readBytes()
            stream.close()
            afd.close()

            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes)
            buf.rewind()

            Interpreter(buf, Interpreter.Options().apply { numThreads = 2 })
        } catch (e: Exception) {
            Log.e(TAG, "loadModel failed: ${e.message}", e)
            null
        }
    }

    /** Log input/output tensor shapes so you can verify them in Logcat */
    private fun logModelShapes() {
        val interp = interpreter ?: return
        try {
            val inShape = interp.getInputTensor(0).shape()
            val outShape = interp.getOutputTensor(0).shape()
            Log.i(TAG, "Model input shape:  ${inShape.toList()}")
            Log.i(TAG, "Model output shape: ${outShape.toList()}")
        } catch (e: Exception) {
            Log.e(TAG, "Could not read tensor shapes: ${e.message}")
        }
    }

    // ── Real inference loop ───────────────────────────────

    private suspend fun runInferenceLoop() {
        val interp = interpreter ?: return

        // Read actual input/output shapes from the model
        val inTensor  = interp.getInputTensor(0)
        val outTensor = interp.getOutputTensor(0)
        val inShape   = inTensor.shape()
        val outShape  = outTensor.shape()
        val numClasses = outShape.last()

        Log.i(TAG, "Input  tensor shape: ${inShape.toList()} dtype: ${inTensor.dataType()}")
        Log.i(TAG, "Output tensor shape: ${outShape.toList()} dtype: ${outTensor.dataType()}")

        // Total float elements the model expects as input
        val inputSize = inShape.fold(1) { acc, v -> acc * v }
        Log.i(TAG, "Input element count: $inputSize")

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(NUM_SAMPLES * 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord.release()
            return
        }

        audioRecord.startRecording()
        Log.i(TAG, "AudioRecord started")

        // Input ByteBuffer: 4 bytes per float × inputSize elements
        val inputBuffer = ByteBuffer
            .allocateDirect(inputSize * 4)
            .order(ByteOrder.nativeOrder())

        // Output ByteBuffer: 4 bytes per float × numClasses
        val outputBuffer = ByteBuffer
            .allocateDirect(numClasses * 4)
            .order(ByteOrder.nativeOrder())

        val pcm   = ShortArray(NUM_SAMPLES)
        val model = ModelType.BEST_MODEL

        try {
            while (currentCoroutineContext().isActive) {
                // 1. Collect one second of PCM from mic
                var samplesRead = 0
                while (samplesRead < NUM_SAMPLES && currentCoroutineContext().isActive) {
                    val n = audioRecord.read(pcm, samplesRead, NUM_SAMPLES - samplesRead)
                    if (n > 0) samplesRead += n else break
                }
                if (!currentCoroutineContext().isActive) break

                // 2. Fill input ByteBuffer with normalised floats
                inputBuffer.rewind()

                // Check if mic is silent (emulator sends all zeros)
                var maxAmplitude = 0
                for (i in 0 until samplesRead) {
                    val abs = Math.abs(pcm[i].toInt())
                    if (abs > maxAmplitude) maxAmplitude = abs
                }
                val isSilent = maxAmplitude < 50  // below noise floor = no real audio

                if (isSilent) {
                    // Post background/idle result — don't run inference on silence
                    AlertRepository.postDetection(
                        DetectionResult(SoundClass.BACKGROUND, 0.99f, 0, model, false)
                    )
                    continue
                }

                val samplesToWrite = minOf(samplesRead, inputSize)
                for (i in 0 until samplesToWrite) {
                    inputBuffer.putFloat(pcm[i] / 32768.0f)
                }
                // Zero-pad if model expects more samples than we read
                for (i in samplesToWrite until inputSize) {
                    inputBuffer.putFloat(0f)
                }
                inputBuffer.rewind()
                outputBuffer.rewind()

                // 3. Run inference
                val t0 = System.currentTimeMillis()
                try {
                    interp.run(inputBuffer, outputBuffer)
                } catch (e: Exception) {
                    Log.e(TAG, "Inference error: ${e.message}", e)
                    break
                }
                val inferMs = (System.currentTimeMillis() - t0).toInt()

                // 4. Read output scores
                outputBuffer.rewind()
                val scores = FloatArray(numClasses) { outputBuffer.float }

                val topIdx   = scores.indices.maxByOrNull { scores[it] } ?: 0
                val topScore = scores[topIdx]
                val topClass = if (topIdx < LABEL_ORDER.size) LABEL_ORDER[topIdx]
                               else SoundClass.BACKGROUND

                val isEmergency = topScore >= THRESHOLD && topClass != SoundClass.BACKGROUND

                Log.d(TAG, "Detection: $topClass @ ${(topScore * 100).toInt()}% in ${inferMs}ms | scores: ${scores.toList()}")

                AlertRepository.postDetection(
                    DetectionResult(topClass, topScore, inferMs, model, isEmergency)
                )

                if (isEmergency) {
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIF_ID, buildNotification("⚠️ ${topClass.label} detected!"))
                }
            }
        } finally {
            try { audioRecord.stop() } catch (_: Exception) {}
            audioRecord.release()
            Log.i(TAG, "AudioRecord released")
        }
    }

    // ── Fallback: mic open but no model ──────────────────
    // Keeps AudioRecord running so the OS doesn't kill the
    // foreground service, but posts no detections.

    private suspend fun runMicIdleLoop() {
        Log.w(TAG, "Running in idle mode — model unavailable")
        while (currentCoroutineContext().isActive) {
            delay(1000)
        }
    }

    // ── Notification ─────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "SoundGuard Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Ongoing audio monitoring notification" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SoundGuard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
