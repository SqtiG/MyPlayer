package com.example.myplayer.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Proste, lekkie magazynowanie ustawień aplikacji (playlisty, tryby Bluetooth, wybrane foldery)
 * w jednym pliku JSON. Dane są czytane synchronicznie przy starcie (plik jest niewielki),
 * a zapisywane w tle, atomowo (plik tymczasowy + zamiana).
 */
class AppStore(private val file: File, scope: CoroutineScope) {

    private val state = MutableStateFlow(load())
    val data: StateFlow<StoreData> = state.asStateFlow()

    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch(Dispatchers.IO) {
            for (request in saveRequests) {
                write(state.value)
            }
        }
    }

    fun update(transform: (StoreData) -> StoreData) {
        state.update(transform)
        saveRequests.trySend(Unit)
    }

    // ---------- Playlisty ----------

    fun createPlaylist(name: String, trackKeys: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        update { it.copy(playlists = it.playlists + Playlist(id, name.trim(), trackKeys.distinct())) }
        return id
    }

    fun renamePlaylist(id: String, name: String) =
        updatePlaylist(id) { it.copy(name = name.trim()) }

    fun deletePlaylist(id: String) = update { d ->
        d.copy(
            playlists = d.playlists.filterNot { it.id == id },
            // tryby wskazujące na usuniętą playlistę wracają do "wszystkich utworów"
            modes = d.modes.map { if (it.playlistId == id) it.copy(playlistId = null) else it },
        )
    }

    fun addToPlaylist(id: String, keys: List<String>) = updatePlaylist(id) { p ->
        val existing = p.trackKeys.toHashSet()
        p.copy(trackKeys = p.trackKeys + keys.distinct().filterNot { it in existing })
    }

    fun removeFromPlaylist(id: String, index: Int) = updatePlaylist(id) { p ->
        if (index in p.trackKeys.indices) {
            p.copy(trackKeys = p.trackKeys.toMutableList().also { it.removeAt(index) })
        } else {
            p
        }
    }

    fun moveInPlaylist(id: String, from: Int, to: Int) = updatePlaylist(id) { p ->
        if (from in p.trackKeys.indices && to in p.trackKeys.indices) {
            val list = p.trackKeys.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            p.copy(trackKeys = list)
        } else {
            p
        }
    }

    private fun updatePlaylist(id: String, transform: (Playlist) -> Playlist) = update { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) transform(it) else it })
    }

    // ---------- Tryby Bluetooth ----------

    fun upsertMode(mode: BtMode) = update { d ->
        val exists = d.modes.any { it.id == mode.id }
        d.copy(modes = if (exists) d.modes.map { if (it.id == mode.id) mode else it } else d.modes + mode)
    }

    fun deleteMode(id: String) = update { d -> d.copy(modes = d.modes.filterNot { it.id == id }) }

    // ---------- Zapis / odczyt ----------

    private fun load(): StoreData {
        if (!file.exists()) return StoreData()
        return try {
            parse(JSONObject(file.readText()))
        } catch (e: Exception) {
            Log.e(TAG, "Nie można odczytać danych aplikacji – start z pustymi ustawieniami", e)
            StoreData()
        }
    }

    private fun write(d: StoreData) {
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(toJson(d).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Nie można zapisać danych aplikacji", e)
        }
    }

    private fun parse(o: JSONObject): StoreData {
        val playlists = o.optJSONArray("playlists").mapObjects { p ->
            Playlist(
                id = p.getString("id"),
                name = p.optString("name", ""),
                trackKeys = p.optJSONArray("tracks").strings(),
            )
        }
        val modes = o.optJSONArray("modes").mapObjects { m ->
            BtMode(
                id = m.getString("id"),
                name = m.optString("name", ""),
                deviceAddress = m.optString("deviceAddress", ""),
                deviceName = m.optString("deviceName", ""),
                playlistId = m.optString("playlistId", "").ifEmpty { null },
                shuffle = m.optBoolean("shuffle", false),
                pauseOnDisconnect = m.optBoolean("pauseOnDisconnect", true),
                enabled = m.optBoolean("enabled", true),
            )
        }
        val selected = if (o.has("selectedFolders")) {
            o.optJSONArray("selectedFolders").strings().toSet()
        } else {
            null
        }
        val last = o.optJSONObject("last")?.let {
            LastPlayback(
                source = it.optString("source", Sources.ALL),
                trackKey = it.optString("trackKey", "").ifEmpty { null },
                positionMs = it.optLong("positionMs", 0L),
            )
        }
        return StoreData(playlists, modes, selected, last)
    }

    private fun toJson(d: StoreData): JSONObject {
        val root = JSONObject()
        root.put("version", 1)

        val playlists = JSONArray()
        d.playlists.forEach { p ->
            val po = JSONObject()
            po.put("id", p.id)
            po.put("name", p.name)
            po.put("tracks", JSONArray(p.trackKeys))
            playlists.put(po)
        }
        root.put("playlists", playlists)

        val modes = JSONArray()
        d.modes.forEach { m ->
            val mo = JSONObject()
            mo.put("id", m.id)
            mo.put("name", m.name)
            mo.put("deviceAddress", m.deviceAddress)
            mo.put("deviceName", m.deviceName)
            if (m.playlistId != null) mo.put("playlistId", m.playlistId)
            mo.put("shuffle", m.shuffle)
            mo.put("pauseOnDisconnect", m.pauseOnDisconnect)
            mo.put("enabled", m.enabled)
            modes.put(mo)
        }
        root.put("modes", modes)

        if (d.selectedFolders != null) {
            root.put("selectedFolders", JSONArray(d.selectedFolders.toList()))
        }

        val last = d.last
        if (last != null) {
            val lo = JSONObject()
            lo.put("source", last.source)
            if (last.trackKey != null) lo.put("trackKey", last.trackKey)
            lo.put("positionMs", last.positionMs)
            root.put("last", lo)
        }
        return root
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else List(length()) { getString(it) }

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else List(length()) { transform(getJSONObject(it)) }

    private companion object {
        const val TAG = "AppStore"
    }
}
