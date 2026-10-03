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
import kotlin.math.*

/**
 * Preprocessing matches the training pipeline exactly:
 *
 * Python (training):
 *   librosa.load(sr=16000, mono=True)
 *   librosa.util.normalize(audio)
 *   librosa.feature.melspectrogram(y, sr=16000, n_fft=1024,
 *       hop_length=512, n_mels=128, fmin=50, fmax=8000)
 *   librosa.power_to_db(mel, ref=np.max)
 *   plt saved as 224×224 PNG with cmap="magma"
 *   loaded by image_dataset_from_directory → pixels [0,255] uint8
 *
 * Label order (Keras alphabetical):
 *   0 = fire_alarm, 1 = noise (Background), 2 = siren
 */
class SoundMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.soundguard.app.START"
        const val ACTION_STOP  = "com.soundguard.app.STOP"

        private const val TAG        = "SoundMonitorService"
        private const val CHANNEL_ID = "soundguard_monitor"
        private const val NOTIF_ID   = 1

        // ── Audio (must match training) ───────────────────
        private const val SR          = 16000
        private const val DURATION_S  = 4        // training clips were 4s
        private const val NUM_SAMPLES = SR * DURATION_S  // 64000

        // ── Mel spectrogram (must match training) ─────────
        private const val N_FFT      = 1024
        private const val HOP_LENGTH = 512
        private const val N_MELS     = 128
        private const val FMIN       = 50.0
        private const val FMAX       = 8000.0

        // ── Model input ───────────────────────────────────
        private const val IMG_SIZE   = 224

        // ── Detection threshold ───────────────────────────
        private const val THRESHOLD  = 0.75f

        // ── Label order: Keras sorts folders alphabetically ──
        // ['fire_alarm'=0, 'noise'=1, 'siren'=2]
        private val LABEL_ORDER = arrayOf(
            SoundClass.FIRE_ALARM,   // 0
            SoundClass.BACKGROUND,   // 1 — 'noise' folder
            SoundClass.SIREN         // 2
        )

        // ── Magma colormap (256 RGB entries) ─────────────
        // Matches cmap="magma" used when saving training images.
        // Values from matplotlib's magma LUT.
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
            255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255
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
            255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255,255
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
            247,247,248,248,249,249,249,250,250,251,251,251,252,252,253,253,254
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
        Log.i(TAG, if (interpreter != null) "Model loaded OK" else "Model load FAILED")
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
        if (AlertRepository.isRunning) return
        AlertRepository.setRunning(true)
        startForeground(NOTIF_ID, buildNotification("Listening for emergency sounds…"))
        detectionJob = scope.launch {
            try {
                if (interpreter != null) runInferenceLoop()
                else runIdleLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Loop crashed: ${e.message}", e)
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
            stream.close(); afd.close()

            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes); buf.rewind()

            val interp = Interpreter(buf, Interpreter.Options().apply { numThreads = 2 })
            Log.i(TAG, "Input  shape: ${interp.getInputTensor(0).shape().toList()}")
            Log.i(TAG, "Output shape: ${interp.getOutputTensor(0).shape().toList()}")
            interp
        } catch (e: Exception) {
            Log.e(TAG, "loadModel: ${e.message}", e)
            null
        }
    }

    // ── Inference loop ────────────────────────────────────

    private suspend fun runInferenceLoop() {
        val interp = interpreter ?: return

        val minBuf = AudioRecord.getMinBufferSize(
            SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(NUM_SAMPLES * 2)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, SR,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed"); audioRecord.release(); return
        }

        audioRecord.startRecording()
        Log.i(TAG, "Recording started")

        // Input:  [1, 224, 224, 3] × 4 bytes/float
        val inputBuffer = ByteBuffer
            .allocateDirect(1 * IMG_SIZE * IMG_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        // Output: [1, 3] × 4 bytes/float
        val numClasses   = interp.getOutputTensor(0).shape().last()
        val outputBuffer = ByteBuffer
            .allocateDirect(numClasses * 4)
            .order(ByteOrder.nativeOrder())

        val pcm        = ShortArray(NUM_SAMPLES)
        val hannWindow = buildHannWindow(N_FFT)
        val melFilters = buildMelFilterbank(N_MELS, N_FFT / 2 + 1, SR, FMIN, FMAX)
        val model      = ModelType.BEST_MODEL

        try {
            while (currentCoroutineContext().isActive) {

                // 1. Record one window of audio
                var samplesRead = 0
                while (samplesRead < NUM_SAMPLES && currentCoroutineContext().isActive) {
                    val n = audioRecord.read(pcm, samplesRead, NUM_SAMPLES - samplesRead)
                    if (n > 0) samplesRead += n else break
                }
                if (!currentCoroutineContext().isActive) break

                // 2. Silence check
                var maxAmp = 0
                for (i in 0 until samplesRead) {
                    val a = abs(pcm[i].toInt()); if (a > maxAmp) maxAmp = a
                }
                if (maxAmp < 100) {
                    Log.d(TAG, "Silence — skipping")
                    AlertRepository.postDetection(
                        DetectionResult(SoundClass.BACKGROUND, 0.99f, 0, model, false,
                            floatArrayOf(0.005f, 0.99f, 0.005f))
                    )
                    continue
                }

                // 3. Normalise PCM → float [-1, 1] + amplitude normalise
                val audio = FloatArray(NUM_SAMPLES) { i ->
                    if (i < samplesRead) pcm[i] / 32768.0f else 0f
                }
                normalizeAmplitude(audio)   // matches librosa.util.normalize

                // 4. Build log-mel spectrogram → magma RGB image → [0,255]
                val t0 = System.currentTimeMillis()
                val pixels = audioToMagmaImage(audio, hannWindow, melFilters)
                val inferMs = (System.currentTimeMillis() - t0).toInt()

                // 5. Fill input buffer
                inputBuffer.rewind()
                for (px in pixels) inputBuffer.putFloat(px)
                inputBuffer.rewind()
                outputBuffer.rewind()

                // 6. Run model
                try {
                    interp.run(inputBuffer, outputBuffer)
                } catch (e: Exception) {
                    Log.e(TAG, "Inference error: ${e.message}", e); break
                }

                // 7. Read scores
                outputBuffer.rewind()
                val scores = FloatArray(numClasses) { outputBuffer.float }
                val topIdx   = scores.indices.maxByOrNull { scores[it] } ?: 0
                val topScore = scores[topIdx]
                val topClass = if (topIdx < LABEL_ORDER.size) LABEL_ORDER[topIdx]
                               else SoundClass.BACKGROUND

                val isEmergency = topScore >= THRESHOLD && topClass != SoundClass.BACKGROUND

                Log.d(TAG, "scores=${scores.map { "%.3f".format(it) }} → $topClass ${(topScore*100).toInt()}% ${inferMs}ms")

                AlertRepository.postDetection(
                    DetectionResult(topClass, topScore, inferMs, model, isEmergency, scores)
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

    // ── Audio → 224×224 magma RGB image ──────────────────
    //
    // Matches training pipeline:
    //   melspectrogram → power_to_db(ref=np.max) → magma colormap → [0,255]

    private fun audioToMagmaImage(
        audio: FloatArray,
        hannWindow: FloatArray,
        melFilters: Array<FloatArray>
    ): FloatArray {
        val halfFft   = N_FFT / 2 + 1
        val numFrames = (audio.size - N_FFT) / HOP_LENGTH + 1

        // STFT power spectrum
        val powerSpec = Array(numFrames) { frame ->
            val offset = frame * HOP_LENGTH
            val re = FloatArray(N_FFT) { i ->
                val s = offset + i
                (if (s < audio.size) audio[s] else 0f) * hannWindow[i]
            }
            val im = FloatArray(N_FFT)
            fft(re, im)
            FloatArray(halfFft) { k -> re[k] * re[k] + im[k] * im[k] }
        }

        // Apply mel filterbank → [numFrames][N_MELS]
        val melSpec = Array(numFrames) { frame ->
            FloatArray(N_MELS) { m ->
                var s = 0f
                for (k in 0 until halfFft) s += melFilters[m][k] * powerSpec[frame][k]
                s.coerceAtLeast(1e-10f)
            }
        }

        // power_to_db(ref=np.max): 10 * log10(S / S.max)
        var globalMax = 1e-10f
        for (frame in melSpec) for (v in frame) if (v > globalMax) globalMax = v
        val logMel = Array(numFrames) { frame ->
            FloatArray(N_MELS) { m ->
                10f * log10(melSpec[frame][m] / globalMax)
            }
        }

        // logMel range is (-∞, 0]. librosa default clip is -80 dB.
        val dbMin = -80f
        // Normalise to [0,1] for colormap mapping
        // value = (db - dbMin) / (0 - dbMin) = db/80 + 1
        // Bilinear resize [numFrames × N_MELS] → [IMG_SIZE × IMG_SIZE]
        // then apply magma colormap → RGB [0,255]

        val pixels = FloatArray(IMG_SIZE * IMG_SIZE * 3)
        var idx = 0

        for (y in 0 until IMG_SIZE) {
            for (x in 0 until IMG_SIZE) {
                // Map to source: x→time(frames), y→mel(flipped, high freq at top)
                val srcX = x * (numFrames - 1).toFloat() / (IMG_SIZE - 1)
                val srcY = (IMG_SIZE - 1 - y) * (N_MELS - 1).toFloat() / (IMG_SIZE - 1)

                val x0 = srcX.toInt().coerceIn(0, numFrames - 1)
                val x1 = (x0 + 1).coerceIn(0, numFrames - 1)
                val y0 = srcY.toInt().coerceIn(0, N_MELS - 1)
                val y1 = (y0 + 1).coerceIn(0, N_MELS - 1)

                val dx = srcX - x0; val dy = srcY - y0

                val db = logMel[x0][y0] * (1-dx)*(1-dy) +
                         logMel[x1][y0] * dx*(1-dy) +
                         logMel[x0][y1] * (1-dx)*dy +
                         logMel[x1][y1] * dx*dy

                // Normalise dB → [0,1] then → colormap index [0,255]
                val norm = ((db - dbMin) / (0f - dbMin)).coerceIn(0f, 1f)
                val ci   = (norm * 255f).toInt().coerceIn(0, 255)

                // Magma RGB [0,255]
                pixels[idx++] = MAGMA_R[ci].toFloat()
                pixels[idx++] = MAGMA_G[ci].toFloat()
                pixels[idx++] = MAGMA_B[ci].toFloat()
            }
        }
        return pixels
    }

    // ── Helpers ───────────────────────────────────────────

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
        val melPts = DoubleArray(nMels + 2) { i -> melToHz(melMin + i*(melMax-melMin)/(nMels+1)) }
        val bins   = DoubleArray(nMels + 2) { i -> floor(melPts[i] * (2*(nFft-1)+1) / sr) }
        return Array(nMels) { m ->
            FloatArray(nFft) { k ->
                val kd = k.toDouble()
                when {
                    kd < bins[m]    -> 0f
                    kd <= bins[m+1] -> ((kd - bins[m]) / (bins[m+1] - bins[m])).toFloat()
                    kd <= bins[m+2] -> ((bins[m+2] - kd) / (bins[m+2] - bins[m+1])).toFloat()
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
                    val nr = uRe*wRe - uIm*wIm; uIm = uRe*wIm + uIm*wRe; uRe = nr
                }
                s += len
            }
            len = len shl 1
        }
    }

    private fun runIdleLoop() { /* model unavailable — service stays alive */ }

    // ── Notification ──────────────────────────────────────

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "SoundGuard Monitor",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Ongoing audio monitoring notification"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SoundGuard").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi).setOngoing(true).setSilent(true).build()
    }
}
