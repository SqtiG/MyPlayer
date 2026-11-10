package com.example.myplayer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.example.myplayer.data.AppStore
import com.example.myplayer.data.AudioCatalog
import com.example.myplayer.data.Track
import com.example.myplayer.data.resolveSource
import com.example.myplayer.data.visibleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

object Channels {
    const val PLAYBACK = "playback"
    const val PROMPT = "bt_prompt"
}

class MusicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                Channels.PLAYBACK,
                getString(R.string.channel_playback),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                Channels.PROMPT,
                getString(R.string.channel_prompt),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }
}

/**
 * Proste "ręczne" wstrzykiwanie zależności. Jest współdzielone przez interfejs, usługę
 * odtwarzania i odbiornik Bluetooth (wszystko działa w jednym procesie).
 */
class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val store = AppStore(File(app.filesDir, "store.json"), scope)
    val catalog = AudioCatalog(app, scope)

    /** Rozwiązuje źródło ("all", "pl:<id>", "folder:<id>") do listy utworów gotowej do odtworzenia. */
    suspend fun tracksForSource(source: String): List<Track> {
        catalog.ensureLoaded()
        val data = store.data.value
        val all = catalog.allTracks.value
        return resolveSource(source, all, all.visibleIn(data.selectedFolders), data.playlists)
    }
}
