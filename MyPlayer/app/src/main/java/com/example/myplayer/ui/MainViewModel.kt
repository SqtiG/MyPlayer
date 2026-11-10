package com.example.myplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myplayer.MusicApp
import com.example.myplayer.data.AppStore
import com.example.myplayer.data.BtMode
import com.example.myplayer.data.FolderInfo
import com.example.myplayer.data.Sources
import com.example.myplayer.data.StoreData
import com.example.myplayer.data.Track
import com.example.myplayer.data.buildFolders
import com.example.myplayer.data.visibleIn
import com.example.myplayer.playback.PlayerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as MusicApp).container
    private val catalog = container.catalog
    val store: AppStore = container.store

    val player = PlayerController(app)

    val data: StateFlow<StoreData> = store.data
    val loading: StateFlow<Boolean> = catalog.loading
    private val allTracks: StateFlow<List<Track>> = catalog.allTracks

    /** Utwory z zaznaczonych folderów – to widzi użytkownik w bibliotece. */
    val visibleTracks: StateFlow<List<Track>> = combine(
        allTracks,
        data.map { it.selectedFolders }.distinctUntilChanged(),
    ) { tracks, selected -> tracks.visibleIn(selected) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Wszystkie foldery z plikami audio (zaznaczone i nie). */
    val folders: StateFlow<List<FolderInfo>> = allTracks
        .map { buildFolders(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Wyszukiwanie utworu po kluczu (np. do okładek i playlist). */
    val trackIndex: StateFlow<Map<String, Track>> = allTracks
        .map { list -> list.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        player.connect()
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch { catalog.refresh() }
    }

    // ---------- Odtwarzanie ----------

    fun playTracks(tracks: List<Track>, startIndex: Int, source: String) =
        player.play(tracks, startIndex, source)

    fun playSource(source: String, shuffle: Boolean) {
        viewModelScope.launch {
            val tracks = container.tracksForSource(source)
            if (tracks.isEmpty()) return@launch
            if (shuffle) player.playShuffled(tracks, source) else player.play(tracks, 0, source)
        }
    }

    /** Test trybu bez Bluetooth: odtwarza jego playlistę od razu na tym telefonie. */
    fun testMode(mode: BtMode) {
        val source = mode.playlistId?.let { Sources.playlist(it) } ?: Sources.ALL
        playSource(source, mode.shuffle)
    }

    // ---------- Foldery ----------

    fun setFolderSelected(folderId: String, selected: Boolean) {
        val allIds = folders.value.map { it.id }.toSet()
        store.update { d ->
            val current = d.selectedFolders ?: allIds
            val result = if (selected) current + folderId else current - folderId
            // zaznaczone wszystkie = "wszystkie foldery" (także przyszłe)
            d.copy(selectedFolders = if (result.containsAll(allIds)) null else result)
        }
    }

    fun selectAllFolders() = store.update { it.copy(selectedFolders = null) }

    fun selectNoFolders() = store.update { it.copy(selectedFolders = emptySet()) }

    fun createPlaylistFromFolder(folderId: String, name: String): String {
        val keys = trackIndex.value.values
            .filter { it.folderId == folderId }
            .sortedBy { it.title.lowercase() }
            .map { it.key }
        return store.createPlaylist(name, keys)
    }

    override fun onCleared() {
        player.release()
    }
}
