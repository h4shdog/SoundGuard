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
 * Uses a private backing state to avoid name clashes with MediaPlayer.isPlaying.
 */
class SoundSamplePlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    // Private backing state — avoids clash with MediaPlayer.isPlaying
    private var _isPlaying by mutableStateOf(false)
    val isPlaying: Boolean get() = _isPlaying

    // Expose currentRes so HistoryCard can check which clip is active
    var currentRes: Int = -1
        private set

    /**
     * If [resId] is already playing → stop.
     * Otherwise → play [resId] (stops any current clip first).
     */
    fun toggle(@RawRes resId: Int) {
        if (_isPlaying && currentRes == resId) {
            stop()
        } else {
            play(resId)
        }
    }

    private fun play(@RawRes resId: Int) {
        stop()
        currentRes = resId
        val mp = MediaPlayer.create(context, resId)
        if (mp != null) {
            mp.setOnCompletionListener { _isPlaying = false }
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
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: Exception) { }
            mp.release()
        }
        mediaPlayer = null
        _isPlaying = false
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
