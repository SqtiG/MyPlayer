package com.example.myplayer.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.myplayer.data.Track
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

data class PlayerState(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    /** Klucz aktualnego utworu (Track.key). */
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Pilot do [PlaybackService] używany przez interfejs. Łączy się z sesją Media3 przez
 * MediaController – dzięki temu odtwarzanie działa niezależnie od cyklu życia ekranu.
 */
class PlayerController(context: Context) {

    private val appContext = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener(
            {
                try {
                    val c = f.get()
                    controller = c
                    c.addListener(listener)
                    publish(c)
                    while (pending.isNotEmpty()) {
                        val action = pending.removeFirst()
                        action(c)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Nie można połączyć z usługą odtwarzania", e)
                    future = null
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        future = null
        pending.clear()
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending.addLast(block)
            connect()
        }
    }

    // ---------- Sterowanie ----------

    fun play(tracks: List<Track>, startIndex: Int, source: String, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem(source) }
        val index = startIndex.coerceIn(0, items.lastIndex)
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, index, 0L)
            c.prepare()
            c.play()
        }
    }

    fun playShuffled(tracks: List<Track>, source: String) {
        if (tracks.isEmpty()) return
        play(tracks, Random.nextInt(tracks.size), source, shuffle = true)
    }

    fun togglePlay() = withController { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = withController { it.seekToNext() }

    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0L

    private fun publish(p: Player) {
        val item = p.currentMediaItem
        val meta = item?.mediaMetadata
        val duration = p.duration
        _state.value = PlayerState(
            hasMedia = item != null,
            isPlaying = p.isPlaying,
            mediaId = item?.mediaId,
            title = meta?.title?.toString().orEmpty(),
            artist = meta?.artist?.toString().orEmpty(),
            durationMs = if (duration != C.TIME_UNSET && duration > 0) duration else 0L,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
        )
    }

    private companion object {
        const val TAG = "PlayerController"
    }
}
