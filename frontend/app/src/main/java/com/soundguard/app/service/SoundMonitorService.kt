package com.soundguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Foreground service — continuously records audio and classifies it using
 * EfficientNetB0 (TFLite) into: fire_alarm / noise / siren.
 *
 * SLIDING WINDOW APPROACH:
 *   • Maintains a 4-second ring buffer of audio.
 *   • Records 1 second of new audio per iteration (HOP_SAMPLES).
 *   • Slides the ring buffer forward and runs inference on the full 4-second window.
 *   → UI updates every ~1 second instead of every 4.
 *
 * Preprocessing matches DataPreprocessing_Hashiras.ipynb exactly:
 *   SR=16000, mono, 4 s  →  librosa.util.normalize
 *   melspectrogram(n_fft=1024, hop_length=512, n_mels=128, fmin=50, fmax=8000)
 *   power_to_db(ref=np.max, top_db=80)  →  cmap="magma"  →  224×224 PNG
 *
 * Keras label order (alphabetical folders): 0=fire_alarm  1=noise  2=siren
 */
class SoundMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.soundguard.app.START"
        const val ACTION_STOP  = "com.soundguard.app.STOP"

        private const val TAG              = "SoundMonitorService"
        private const val CHANNEL_ID       = "soundguard_monitor"
        private const val ALERT_CHANNEL_ID = "soundguard_alert"
        private const val NOTIF_ID         = 1
        private const val ALERT_NOTIF_ID   = 2

        // ── Audio (must match training) ───────────────────
        private const val SR          = 16000
        private const val DURATION_S  = 4
        private const val NUM_SAMPLES = SR * DURATION_S   // 64 000 — full model window

        // ── Sliding window ────────────────────────────────
        // Record HOP_SAMPLES (~1 s) of new audio each iteration, then
        // slide the 4-second ring buffer forward. UI updates every ~1 s.
        private const val HOP_DURATION_S = 1
        private const val HOP_SAMPLES    = SR * HOP_DURATION_S  // 16 000

        // ── Spectrogram (must match training) ─────────────
        private const val N_FFT      = 1024
        private const val HOP_LENGTH = 512
        private const val N_MELS     = 128
        private const val FMIN       = 50.0
        private const val FMAX       = 8000.0

        // ── Model ─────────────────────────────────────────
        private const val IMG_SIZE  = 224
        private const val THRESHOLD = 0.75f

        // Keras alphabetical folder sort: fire_alarm=0  noise=1  siren=2
        private val LABEL_ORDER = arrayOf(
            SoundClass.FIRE_ALARM,
            SoundClass.BACKGROUND,
            SoundClass.SIREN
        )

        // ── Magma colormap LUT (256 entries) ─────────────
        private val MAGMA_R = intArrayOf(
            0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,10,10,11,11,12,12,13,14,14,15,15,16,17,17,
            18,18,19,20,20,21,21,22,23,23,24,25,25,26,26,27,28,29,29,30,31,31,32,33,34,34,
            35,36,37,37,38,39,40,41,41,42,43,44,45,46,47,47,48,49,50,51,52,53,54,55,56,57,
            58,59,60,61,62,63,64,65,66,67,68,69,71,72,73,74,75,76,78,79,80,81,82,84,85,86,
            87,89,90,91,93,94,95,97,98,99,101,102,103,105,106,108,109,111,112,114,115,117,
            118,120,121,123,124,126,128,129,131,132,134,136,137,139,141,142,144,146,147,149,
            151,152,154,156,158,159,161,163,165,167,168,170,172,174,176,178,180,181,183,185,
            187,189,191,193,195,197,199,201,203,205,207,209,211,213,215,217,219,221,223,225,
            227,229,231,233,235,236,238,240,242,244,246,248,250,252,253,253,254,254,254,254,
            254,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
            255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
            255,255,255
        )
        private val MAGMA_G = intArrayOf(
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
            0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,2,2,2,2,3,3,3,4,4,
            4,5,5,5,6,6,7,7,8,8,9,9,10,10,11,12,12,13,13,14,15,15,16,17,18,18,
            19,20,21,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,38,39,40,
            41,42,44,45,46,48,49,50,52,53,55,56,57,59,60,62,63,65,66,68,69,71,
            73,74,76,77,79,81,82,84,86,87,89,91,92,94,96,98,99,101,103,105,107,
            108,110,112,114,116,118,120,121,123,125,127,129,131,133,135,137,139,
            141,143,145,148,150,152,154,156,158,160,163,165,167,169,172,174,176,
            178,181,183,185,188,190,192,195,197,200,202,204,207,209,212,214,217,
            219,221,224,226,228,231,233,235,238,240,242,244,246,248,250,252,253,
            253,253,254,254,254,254,254,255,255,255,255,255,255,255,255,255,255,
            255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255
        )
        private val MAGMA_B = intArrayOf(
            4,5,6,7,8,9,10,11,13,14,15,16,18,19,20,22,23,24,26,27,29,30,32,33,
            35,36,38,40,41,43,45,46,48,50,52,53,55,57,59,61,63,64,66,68,70,72,
            74,76,78,80,82,84,86,88,90,92,94,96,98,100,102,104,106,108,110,112,
            114,116,117,119,121,123,125,127,128,130,132,134,135,137,139,140,142,
            144,145,147,148,150,151,153,154,156,157,159,160,162,163,165,166,167,
            169,170,171,173,174,175,177,178,179,180,182,183,184,185,186,187,188,
            190,191,192,193,194,195,196,197,198,199,200,201,202,203,204,205,206,
            206,207,208,209,210,211,211,212,213,214,214,215,216,217,217,218,219,
            219,220,221,221,222,222,223,224,224,225,225,226,226,227,227,228,228,
            229,229,230,230,231,231,231,232,232,233,233,233,234,234,234,235,235,
            235,236,236,236,237,237,237,238,238,238,239,239,239,240,240,240,241,
            241,241,242,242,242,243,243,243,244,244,244,245,245,245,246,246,247,
            247,247,248,248,249,249,249,250,250,251,251,251,252,252,253,253,254,
            254,254,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,
            255,255,255
        )
    }

    // Dispatchers.IO: designed for long-running blocking work (AudioRecord.read blocks)
    private val serviceJob = SupervisorJob()
    private val scope      = CoroutineScope(serviceJob + Dispatchers.IO)
    private var detectionJob: Job? = null
    private var interpreter : Interpreter? = null

    // ── Pre-allocated DSP buffers (zero heap allocation on hot path) ──
    private val halfFft   = N_FFT / 2 + 1
    private val numFrames = (NUM_SAMPLES - N_FFT) / HOP_LENGTH + 1
    private val fftRe     = FloatArray(N_FFT)
    private val fftIm     = FloatArray(N_FFT)
    private val powerSpec = Array(numFrames) { FloatArray(halfFft) }
    private val melSpec   = Array(numFrames) { FloatArray(N_MELS) }
    private val logMel    = Array(numFrames) { FloatArray(N_MELS) }
    private lateinit var hannWindow : FloatArray
    private lateinit var melFilters : Array<FloatArray>

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        hannWindow  = buildHannWindow(N_FFT)
        melFilters  = buildMelFilterbank(N_MELS, halfFft, SR, FMIN, FMAX)
        interpreter = loadModel()
        Log.i(TAG, if (interpreter != null)
            "Model loaded — in=${interpreter!!.getInputTensor(0).shape().toList()}" +
            " out=${interpreter!!.getOutputTensor(0).shape().toList()}"
        else "Model FAILED to load")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopMonitoring(); return START_NOT_STICKY }
            else        -> startMonitoring()   // ACTION_START or null (OS restart)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        scope.cancel()
        try { interpreter?.close() } catch (_: Exception) {}
        interpreter = null
        AlertRepository.setRunning(false)
        super.onDestroy()
    }

    // ── Start / stop ──────────────────────────────────────────

    private fun startMonitoring() {
        // Cancel any stale job from a previous crash before re-starting
        detectionJob?.cancel()
        detectionJob = null
        AlertRepository.setRunning(true)
        startForeground(NOTIF_ID, buildForegroundNotification("Listening for fire alarms and sirens…"))
        detectionJob = scope.launch {
            try {
                if (interpreter != null) runInferenceLoop()
                else {
                    AlertRepository.postError("Model failed to load — please reinstall the app.")
                    AlertRepository.setRunning(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Loop crashed: ${e.message}", e)
                AlertRepository.postError("Monitoring stopped: ${e.message}")
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

    // ── Load TFLite model ─────────────────────────────────────

    private fun loadModel(): Interpreter? = try {
        val afd    = assets.openFd("BestModel.tflite")
        val stream = afd.createInputStream()
        val bytes  = stream.readBytes()
        stream.close(); afd.close()
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes); buf.rewind()
        Interpreter(buf, Interpreter.Options().apply { numThreads = 2 })
    } catch (e: Exception) {
        Log.e(TAG, "loadModel: ${e.message}", e); null
    }

    // ── Main inference loop (Dispatchers.IO) ──────────────────
    //
    // Sliding window:
    //   ringBuf holds the last 4 s of float audio.
    //   Each iteration reads HOP_SAMPLES (~1 s) of new PCM, shifts ringBuf
    //   left by HOP_SAMPLES, appends the new chunk, then runs inference.
    //   → result posted to UI every ~1 second.

    private suspend fun runInferenceLoop() {
        val interp = interpreter ?: return

        val minBuf = AudioRecord.getMinBufferSize(
            SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            AlertRepository.postError("Microphone unavailable on this device.")
            return
        }
        // Large hardware buffer so samples are not dropped during inference
        val bufSize = maxOf(minBuf * 8, NUM_SAMPLES * 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SR,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            AlertRepository.postError("Cannot open microphone — check permissions.")
            return
        }

        audioRecord.startRecording()
        Log.i(TAG, "Recording started  SR=$SR  bufSize=$bufSize")

        val numClasses   = interp.getOutputTensor(0).shape().last()
        val inputBuffer  = ByteBuffer.allocateDirect(IMG_SIZE * IMG_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        val outputBuffer = ByteBuffer.allocateDirect(numClasses * 4).order(ByteOrder.nativeOrder())

        // ── Continuous ring buffer (4 s at 16 kHz = 64 000 samples) ──────
        // AudioRecord streams into `capturePcm` in small hardware chunks.
        // We accumulate chunks into `ringBuf` using a write-head pointer.
        // Once the ring is primed (write-head has lapped once), we run
        // inference on every new chunk — no stopping, no restart.
        val ringBuf    = FloatArray(NUM_SAMPLES)        // circular float buffer
        var writeHead  = 0                              // next write position in ringBuf
        var totalRead  = 0                              // total shorts read (for priming check)

        // Small capture chunk — sized to match the hardware interrupt period.
        // 2048 shorts ≈ 128 ms at 16 kHz, small enough for responsive UI.
        val CHUNK      = 2048
        val capturePcm = ShortArray(CHUNK)
        val audio      = FloatArray(NUM_SAMPLES)        // normalised copy for inference

        // How many samples we need before the first inference (full 4-s window)
        val primingSamples = NUM_SAMPLES

        // How many new samples to accumulate between inferences.
        // HOP_SAMPLES = 1 s → inference fires once per second.
        var samplesUntilNextInference = primingSamples

        try {
            while (currentCoroutineContext().isActive) {

                // ── 1. Read one hardware chunk (blocking, ~128 ms) ───────
                val n = audioRecord.read(capturePcm, 0, CHUNK)
                when {
                    n <= 0 -> {
                        if (n == AudioRecord.ERROR_DEAD_OBJECT ||
                            n == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "audioRecord.read fatal error: $n")
                            AlertRepository.postError("Microphone error — tap Start to retry.")
                        }
                        // n == 0 or ERROR_BAD_VALUE → just try again next iteration
                        continue
                    }
                }
                if (!currentCoroutineContext().isActive) break

                // ── 2. Write chunk into ring buffer (wraps around) ───────
                var written = 0
                while (written < n) {
                    ringBuf[writeHead] = capturePcm[written] / 32768.0f
                    writeHead = (writeHead + 1) % NUM_SAMPLES
                    written++
                }
                totalRead += n
                samplesUntilNextInference -= n

                // ── 3. Still priming — ring not yet full ─────────────────
                if (totalRead < primingSamples) {
                    AlertRepository.postDetection(
                        DetectionResult(
                            soundClass  = SoundClass.BACKGROUND,
                            confidence  = 0f,
                            inferenceMs = 0,
                            model       = ModelType.BEST_MODEL,
                            isEmergency = false,
                            allScores   = floatArrayOf(0f, 0f, 0f)
                        )
                    )
                    continue
                }

                // ── 4. Not yet time for next inference ───────────────────
                if (samplesUntilNextInference > 0) continue
                samplesUntilNextInference = HOP_SAMPLES   // reset for next hop

                // ── 5. Silence gate ───────────────────────────────────────
                // Compute RMS of the last chunk. On a real device, fire alarms
                // are loud (RMS >> 500). On the emulator the virtual mic may
                // produce low-level noise (RMS < 300) that looks like fire alarm
                // to the model after normalizeAmplitude() amplifies it to full
                // scale. We skip inference for very quiet chunks to avoid false
                // positives when nothing is actually playing.
                //
                // Threshold guide (16-bit PCM, range ±32768):
                //   0        → disabled (always run inference — use this on emulator)
                //   1–300    → emulator virtual-mic noise floor / near-silence
                //   300–1000 → very quiet room / HVAC hum
                //   1000+    → audible sound (speech, alarms, etc.)
                //
                // Set to 0 on the emulator so the model always runs and the
                // confidence bars update live. On a real device you can raise
                // this back to ~300 to avoid false positives from mic noise.
                val RMS_SILENCE_THRESHOLD = 0
                var sumSq = 0.0
                val checkLen = minOf(n, CHUNK)
                for (i in 0 until checkLen) {
                    val a = capturePcm[i].toDouble(); sumSq += a * a
                }
                val rms = sqrt(sumSq / checkLen).toInt()
                Log.v(TAG, "chunk RMS=$rms")

                if (rms <= RMS_SILENCE_THRESHOLD) {
                    AlertRepository.postDetection(
                        DetectionResult(
                            soundClass  = SoundClass.BACKGROUND,
                            confidence  = 0.99f,
                            inferenceMs = 0,
                            model       = ModelType.BEST_MODEL,
                            isEmergency = false,
                            allScores   = floatArrayOf(0.005f, 0.99f, 0.005f)
                        )
                    )
                    continue
                }

                // ── 6. Linearise ring buffer into contiguous `audio` ─────
                // writeHead points to the oldest sample (ring has just been
                // written past it). Copy oldest→newest into audio[0..N-1].
                val oldest = writeHead   // oldest sample position
                val part1  = NUM_SAMPLES - oldest
                System.arraycopy(ringBuf, oldest, audio, 0,     part1)
                System.arraycopy(ringBuf, 0,      audio, part1, oldest)
                normalizeAmplitude(audio)

                // ── 7. Build 224×224 magma spectrogram ───────────────────
                buildMagmaImage(audio, inputBuffer)
                inputBuffer.rewind()
                outputBuffer.rewind()

                // ── 8. TFLite inference ───────────────────────────────────
                val t0 = System.currentTimeMillis()
                try {
                    interp.run(inputBuffer, outputBuffer)
                } catch (e: Exception) {
                    Log.e(TAG, "TFLite: ${e.message}", e)
                    AlertRepository.postError("Inference failed: ${e.message}")
                    break
                }
                val inferMs = (System.currentTimeMillis() - t0).toInt()

                // ── 9. Read scores ────────────────────────────────────────
                outputBuffer.rewind()
                val scores   = FloatArray(numClasses) { outputBuffer.float }
                val topIdx   = scores.indices.maxByOrNull { scores[it] } ?: 1
                val topScore = scores[topIdx]
                val topClass = if (topIdx < LABEL_ORDER.size) LABEL_ORDER[topIdx]
                               else SoundClass.BACKGROUND
                val isEmergency = topScore >= THRESHOLD && topClass != SoundClass.BACKGROUND

                Log.d(TAG,
                    "fire=${(scores[0]*100).toInt()}%  " +
                    "noise=${(scores[1]*100).toInt()}%  " +
                    "siren=${(scores[2]*100).toInt()}%  " +
                    "→ ${topClass.label} ${(topScore*100).toInt()}%  ${inferMs}ms"
                )

                // ── 10. Push result to UI ─────────────────────────────────
                AlertRepository.postDetection(
                    DetectionResult(topClass, topScore, inferMs, ModelType.BEST_MODEL, isEmergency, scores)
                )

                // ── 11. Emergency alert ───────────────────────────────────
                if (isEmergency) {
                    withContext(Dispatchers.Main) { sendEmergencyAlert(topClass, topScore) }
                }
            }
        } finally {
            try { audioRecord.stop() } catch (_: Exception) {}
            audioRecord.release()
            Log.i(TAG, "AudioRecord released")
        }
    }

    // ── Build 224×224 magma image directly into a ByteBuffer ──
    // Uses class-level pre-allocated arrays — zero heap allocation.

    private fun buildMagmaImage(audio: FloatArray, out: ByteBuffer) {
        // STFT
        for (frame in 0 until numFrames) {
            val offset = frame * HOP_LENGTH
            for (i in 0 until N_FFT) {
                val s = offset + i
                fftRe[i] = (if (s < audio.size) audio[s] else 0f) * hannWindow[i]
                fftIm[i] = 0f
            }
            fft(fftRe, fftIm)
            val ps = powerSpec[frame]
            for (k in 0 until halfFft) ps[k] = fftRe[k] * fftRe[k] + fftIm[k] * fftIm[k]
        }

        // Mel filterbank
        for (frame in 0 until numFrames) {
            val ps = powerSpec[frame]
            val ms = melSpec[frame]
            for (m in 0 until N_MELS) {
                var s = 0f
                val filt = melFilters[m]
                for (k in 0 until halfFft) s += filt[k] * ps[k]
                ms[m] = if (s < 1e-10f) 1e-10f else s
            }
        }

        // power_to_db(ref=np.max), floor at -80 dB
        var globalMax = 1e-10f
        for (frame in 0 until numFrames) {
            val ms = melSpec[frame]
            for (m in 0 until N_MELS) if (ms[m] > globalMax) globalMax = ms[m]
        }
        for (frame in 0 until numFrames) {
            val ms = melSpec[frame]
            val lm = logMel[frame]
            for (m in 0 until N_MELS) {
                val db = 10f * log10(ms[m] / globalMax)
                lm[m] = if (db < -80f) -80f else db
            }
        }

        // Bilinear resize to 224×224, apply magma LUT, write floats
        out.rewind()
        for (y in 0 until IMG_SIZE) {
            for (x in 0 until IMG_SIZE) {
                val srcX = x * (numFrames - 1).toFloat() / (IMG_SIZE - 1)
                val srcY = (IMG_SIZE - 1 - y) * (N_MELS - 1).toFloat() / (IMG_SIZE - 1)
                val x0 = srcX.toInt().coerceIn(0, numFrames - 1)
                val x1 = (x0 + 1).coerceIn(0, numFrames - 1)
                val y0 = srcY.toInt().coerceIn(0, N_MELS - 1)
                val y1 = (y0 + 1).coerceIn(0, N_MELS - 1)
                val dx = srcX - x0; val dy = srcY - y0
                val db = logMel[x0][y0] * (1-dx)*(1-dy) +
                         logMel[x1][y0] * dx     *(1-dy) +
                         logMel[x0][y1] * (1-dx) * dy    +
                         logMel[x1][y1] * dx      * dy
                val ci = (((db + 80f) / 80f).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 254)
                out.putFloat(MAGMA_R[ci].toFloat())
                out.putFloat(MAGMA_G[ci].toFloat())
                out.putFloat(MAGMA_B[ci].toFloat())
            }
        }
    }

    // ── Emergency alert ───────────────────────────────────────

    private fun sendEmergencyAlert(soundClass: SoundClass, confidence: Float) {
        val title = when (soundClass) {
            SoundClass.FIRE_ALARM -> "🔥 Fire Alarm Detected!"
            SoundClass.SIREN      -> "🚨 Siren Detected!"
            else                  -> return
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
            ALERT_NOTIF_ID,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText("Confidence: ${(confidence * 100).toInt()}% · EfficientNetB0")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
        )
        vibrate(longArrayOf(0L, 400L, 200L, 400L, 200L, 400L))
        Log.i(TAG, "Alert: $title  ${(confidence*100).toInt()}%")
    }

    private fun vibrate(pattern: LongArray) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate: ${e.message}")
        }
    }

    // ── DSP helpers ───────────────────────────────────────────

    private fun normalizeAmplitude(audio: FloatArray) {
        var maxAbs = 1e-10f
        for (v in audio) { val a = abs(v); if (a > maxAbs) maxAbs = a }
        for (i in audio.indices) audio[i] /= maxAbs
    }

    private fun buildHannWindow(n: Int) = FloatArray(n) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
    }

    private fun buildMelFilterbank(
        nMels: Int, nFft: Int, sr: Int, fmin: Double, fmax: Double
    ): Array<FloatArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
        val melMin = hzToMel(fmin); val melMax = hzToMel(fmax)
        val melPts = DoubleArray(nMels + 2) { i -> melToHz(melMin + i * (melMax - melMin) / (nMels + 1)) }
        // bins[i] maps a mel center frequency to an FFT bin index.
        // Clamp to [0, nFft-1] so array accesses in the inner loop never go out of bounds.
        val bins = DoubleArray(nMels + 2) { i ->
            floor(melPts[i] * (2.0 * (nFft - 1)) / sr).coerceIn(0.0, (nFft - 1).toDouble())
        }
        return Array(nMels) { m ->
            FloatArray(nFft) { k ->
                val kd = k.toDouble()
                when {
                    kd < bins[m]    -> 0f
                    kd <= bins[m+1] -> {
                        val denom = bins[m+1] - bins[m]
                        if (denom < 1e-10) 0f else ((kd - bins[m]) / denom).toFloat()
                    }
                    kd <= bins[m+2] -> {
                        val denom = bins[m+2] - bins[m+1]
                        if (denom < 1e-10) 0f else ((bins[m+2] - kd) / denom).toFloat()
                    }
                    else            -> 0f
                }
            }
        }
    }

    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size; if (n <= 1) return
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat(); val wIm = sin(ang).toFloat()
            var s = 0
            while (s < n) {
                var uRe = 1f; var uIm = 0f
                for (k in 0 until len / 2) {
                    val a = s+k; val b = s+k+len/2
                    val tRe = uRe*re[b] - uIm*im[b]
                    val tIm = uRe*im[b] + uIm*re[b]
                    re[b] = re[a]-tRe; im[b] = im[a]-tIm
                    re[a] += tRe;      im[a] += tIm
                    val nr = uRe*wRe - uIm*wIm
                    uIm = uRe*wIm + uIm*wRe; uRe = nr
                }
                s += len
            }
            len = len shl 1
        }
    }

    // ── Notifications ─────────────────────────────────────────

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SoundGuard Monitor", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Persistent notification while audio monitoring is active"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "SoundGuard Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fire alarm and siren detection alerts"
                enableVibration(true)
                enableLights(true)
            }
        )
    }

    private fun buildForegroundNotification(text: String): Notification {
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
