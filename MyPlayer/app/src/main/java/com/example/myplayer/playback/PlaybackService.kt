package com.example.myplayer.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.myplayer.AppContainer
import com.example.myplayer.Channels
import com.example.myplayer.MainActivity
import com.example.myplayer.MusicApp
import com.example.myplayer.R
import com.example.myplayer.data.LastPlayback
import com.example.myplayer.data.Sources
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Usługa odtwarzania (Media3 MediaSessionService).
 *
 *  - gra muzykę w tle i pokazuje powiadomienie z przyciskami,
 *  - obsługuje przyciski słuchawek / samochodu (przez MediaSession),
 *  - uruchamia "tryb Bluetooth": po połączeniu z urządzeniem sama zaczyna odtwarzać playlistę.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startJob: Job? = null

    private val container: AppContainer
        get() = (application as MusicApp).container

    override fun onCreate() {
        super.onCreate()
        instance = this

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // pauza, gdy słuchawki/samochód się rozłączą (np. A2DP odłączone)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(persistListener)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityPendingIntent())
            .setCallback(SessionCallback())
            .build()

        // To samo id powiadomienia co w promoteToForeground(), żeby powiadomienie
        // "Łączenie…" płynnie zamieniło się w normalne powiadomienie odtwarzacza.
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(Channels.PLAYBACK)
            .setChannelName(R.string.channel_playback)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_music)
        setMediaNotificationProvider(provider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_START_MODE) {
            val modeId = intent.getStringExtra(EXTRA_MODE_ID)
            if (modeId != null) handleStartMode(modeId)
        }
        return result
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Zamknięcie aplikacji z listy ostatnich: gra dalej, jeśli muzyka leci (np. w samochodzie).
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        instance = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------------------------
    // Tryb Bluetooth
    // ------------------------------------------------------------------------------------

    private fun handleStartMode(modeId: String) {
        // Usługa została uruchomiona przez startForegroundService() – MUSIMY od razu wejść
        // w tryb pierwszoplanowy (limit czasu to kilka sekund), a to jednocześnie spełnia
        // wymóg Androida 17: odtwarzanie z tła wymaga działającej usługi pierwszoplanowej.
        if (!promoteToForeground()) {
            stopSelf()
            return
        }
        startJob?.cancel()
        startJob = scope.launch {
            val mode = container.store.data.value.modes.firstOrNull { it.id == modeId }
            if (mode == null) {
                stopIfIdle()
                return@launch
            }
            val currentMode = player.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_MODE_ID)
            if (player.isPlaying && currentMode == mode.id) return@launch // już gra

            // Czekamy, aż system podłączy wyjście audio A2DP – inaczej muzyka na chwilę
            // zagrałaby z głośnika telefonu, zanim samochód przejmie dźwięk.
            if (!waitForBluetoothAudio(mode.deviceAddress)) {
                Log.w(TAG, "Nie wykryto wyjścia audio Bluetooth dla ${mode.deviceName} – rezygnuję")
                stopIfIdle()
                return@launch
            }

            val source = mode.playlistId?.let { Sources.playlist(it) } ?: Sources.ALL
            val tracks = container.tracksForSource(source)
            if (tracks.isEmpty()) {
                Log.w(TAG, "Tryb ${mode.name}: brak utworów do odtworzenia")
                stopIfIdle()
                return@launch
            }

            player.shuffleModeEnabled = mode.shuffle
            player.repeatMode = Player.REPEAT_MODE_ALL
            val startIndex = if (mode.shuffle) Random.nextInt(tracks.size) else 0
            player.setMediaItems(tracks.map { it.toMediaItem(source, mode.id) }, startIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    /** Wywoływane przez [com.example.myplayer.bluetooth.BluetoothReceiver] po rozłączeniu. */
    fun onBluetoothDisconnected(address: String) {
        val modes = container.store.data.value.modes
            .filter { it.deviceAddress.equals(address, ignoreCase = true) }
        if (modes.isEmpty()) return

        startJob?.cancel()
        val currentMode = player.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_MODE_ID)
        if (modes.any { it.id == currentMode && it.pauseOnDisconnect }) {
            player.pause()
        }
        if (!player.isPlaying) stopIfIdle()
    }

    private suspend fun waitForBluetoothAudio(address: String): Boolean {
        val audioManager = getSystemService(AudioManager::class.java)
        val started = SystemClock.elapsedRealtime()
        while (true) {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isBluetoothMediaOutput() }
            if (outputs.any { it.address.equals(address, ignoreCase = true) }) return true
            val elapsed = SystemClock.elapsedRealtime() - started
            // Część urządzeń zgłasza inny adres niż MAC z broadcastu – po 10 s akceptujemy
            // dowolne podłączone wyjście Bluetooth.
            if (elapsed > 10_000 && outputs.isNotEmpty()) return true
            if (elapsed > 25_000) return false
            delay(400)
        }
    }

    private fun AudioDeviceInfo.isBluetoothMediaOutput(): Boolean {
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) return true
        if (type == AudioDeviceInfo.TYPE_HEARING_AID) return true
        if (Build.VERSION.SDK_INT >= 31 &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) return true
        return false
    }

    private fun promoteToForeground(): Boolean {
        val notification = NotificationCompat.Builder(this, Channels.PLAYBACK)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_connecting))
            .setContentIntent(activityPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "System nie pozwolił uruchomić usługi pierwszoplanowej", e)
            false
        }
    }

    private fun stopIfIdle() {
        if (!player.isPlaying && !player.playWhenReady) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun activityPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // ------------------------------------------------------------------------------------
    // Zapamiętywanie ostatniego odtwarzania (do wznawiania przyciskiem "play")
    // ------------------------------------------------------------------------------------

    private val persistListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveLast()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            saveLast()
        }
    }

    private fun saveLast() {
        val item = player.currentMediaItem ?: return
        val source = item.mediaMetadata.extras?.getString(EXTRA_SOURCE) ?: Sources.ALL
        val last = LastPlayback(source, item.mediaId, player.currentPosition)
        container.store.update { it.copy(last = last) }
    }

    private inner class SessionCallback : MediaSession.Callback {

        /**
         * Wywoływane, gdy system/samochód wyśle "play", a kolejka jest pusta (np. po zamknięciu
         * aplikacji) – odtwarzamy ostatnio słuchaną playlistę od ostatniego utworu.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            scope.launch {
                try {
                    val last = container.store.data.value.last
                    val source = last?.source ?: Sources.ALL
                    val tracks = container.tracksForSource(source)
                    if (tracks.isEmpty()) {
                        future.setException(UnsupportedOperationException("Brak utworów"))
                    } else {
                        val index = tracks.indexOfFirst { it.key == last?.trackKey }
                        val items = tracks.map { it.toMediaItem(source) }
                        future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                items,
                                if (index >= 0) index else 0,
                                if (index >= 0) last?.positionMs ?: 0L else 0L,
                            )
                        )
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_MODE = "com.example.myplayer.action.START_MODE"

        @Volatile
        var instance: PlaybackService? = null
            private set

        fun startIntent(context: Context, modeId: String): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_START_MODE)
                .putExtra(EXTRA_MODE_ID, modeId)
    }
}
