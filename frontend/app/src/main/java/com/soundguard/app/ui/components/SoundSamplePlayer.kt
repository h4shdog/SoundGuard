package com.soundguard.app.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Compose-friendly MediaPlayer wrapper.
 * Supports playback from both raw resources (@RawRes) and absolute file paths.
 */
class SoundSamplePlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    // Private backing state — avoids clash with MediaPlayer.isPlaying
    private var _isPlaying by mutableStateOf(false)
    val isPlaying: Boolean get() = _isPlaying

    // Track what is currently loaded so the UI can check toggle state.
    // One of these will be set; the other will be the sentinel value.
    var currentRes: Int = -1
        private set
    var currentPath: String? = null
        private set

    // ── Raw resource playback ─────────────────────────────────

    fun toggle(@RawRes resId: Int) {
        if (_isPlaying && currentRes == resId) stop() else playRes(resId)
    }

    private fun playRes(@RawRes resId: Int) {
        stop()
        currentRes  = resId
        currentPath = null
        val mp = MediaPlayer.create(context, resId)
        startMp(mp)
    }

    // ── File path playback ────────────────────────────────────

    /**
     * Toggle playback of the WAV file at [path].
     * If the same path is already playing it will be stopped.
     */
    fun togglePath(path: String) {
        if (_isPlaying && currentPath == path) stop() else playPath(path)
    }

    private fun playPath(path: String) {
        stop()
        currentRes  = -1
        currentPath = path
        val mp = try {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
            }
        } catch (e: Exception) {
            _isPlaying = false
            return
        }
        startMp(mp)
    }

    // ── Shared start logic ────────────────────────────────────

    private fun startMp(mp: MediaPlayer?) {
        if (mp != null) {
            mp.setOnCompletionListener { _isPlaying = false; currentPath = null; currentRes = -1 }
            mp.start()
            mediaPlayer = mp
            _isPlaying = true
        } else {
            _isPlaying = false
        }
    }

    fun stop() {
        val mp = mediaPlayer
        if (mp != null) {
            try { if (mp.isPlaying) mp.stop() } catch (_: Exception) { }
            mp.release()
        }
        mediaPlayer = null
        _isPlaying  = false
    }

    fun release() = stop()
}

@Composable
fun rememberSoundSamplePlayer(): SoundSamplePlayer {
    val context = LocalContext.current
    val player = remember { SoundSamplePlayer(context) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.release()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    return player
}
