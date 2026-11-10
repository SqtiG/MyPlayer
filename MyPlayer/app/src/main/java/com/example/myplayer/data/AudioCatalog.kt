package com.example.myplayer.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.myplayer.util.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Katalog wszystkich plików audio w telefonie (przez MediaStore).
 * Filtrowanie po wybranych folderach odbywa się wyżej – tu trzymamy pełną listę.
 */
class AudioCatalog(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var loadedOnce = false
    private var pendingRefresh: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            requestRefresh()
        }
    }

    init {
        // Gdy w telefonie pojawią się/znikną pliki audio, lista odświeży się sama.
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }

    fun requestRefresh() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch {
            delay(1000)
            refresh()
        }
    }

    suspend fun refresh() {
        mutex.withLock {
            if (Permissions.hasAudio(context)) {
                _loading.value = true
                try {
                    _tracks.value = withContext(Dispatchers.IO) { queryTracks() }
                    loadedOnce = true
                } catch (e: Exception) {
                    Log.e(TAG, "Błąd odczytu MediaStore", e)
                } finally {
                    _loading.value = false
                }
            } else {
                _tracks.value = emptyList()
            }
        }
    }

    suspend fun ensureLoaded() {
        if (!loadedOnce) refresh()
    }

    private fun queryTracks(): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.VOLUME_NAME,
        )
        // Pomijamy dzwonki, powiadomienia i alarmy – to nie jest muzyka.
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0"

        val result = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val relCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val volCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val displayName = c.getString(nameCol) ?: continue
                val relativePath = c.getString(relCol).orEmpty()
                val volume = c.getString(volCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY

                val title = c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: displayName.substringBeforeLast('.')
                val artist = c.getString(artistCol)
                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    .orEmpty()
                val album = c.getString(albumCol)
                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    .orEmpty()

                result += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durationCol),
                    folderId = "$volume:$relativePath",
                    folderLabel = folderLabel(volume, relativePath),
                    key = "$volume:$relativePath$displayName",
                )
            }
        }

        // Sortowanie zgodne z polskimi regułami alfabetu (ą, ć, ę, ł…)
        val collator = Collator.getInstance(Locale.getDefault())
        return result.sortedWith { a, b -> collator.compare(a.title, b.title) }
    }

    private fun folderLabel(volume: String, relativePath: String): String {
        val path = relativePath.trim('/').ifEmpty { "(katalog główny)" }
        return if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) path else "[$volume] $path"
    }

    private companion object {
        const val TAG = "AudioCatalog"
    }
}
