package com.example.myplayer.data

import android.net.Uri

/** Pojedynczy plik audio z MediaStore. */
data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    /** Identyfikator folderu: "wolumin:ścieżka/" – np. "external_primary:Music/Rock/". */
    val folderId: String,
    /** Czytelna nazwa folderu, np. "Music/Rock". */
    val folderLabel: String,
    /**
     * Stabilny klucz utworu: "wolumin:ścieżka/nazwa_pliku". Playlisty przechowują klucze,
     * a nie identyfikatory MediaStore, dzięki czemu przeżywają ponowne indeksowanie plików.
     */
    val key: String,
)

data class FolderInfo(
    val id: String,
    val label: String,
    val trackCount: Int,
)

data class Playlist(
    val id: String,
    val name: String,
    val trackKeys: List<String>,
)

/**
 * Tryb Bluetooth: po połączeniu z urządzeniem [deviceAddress] aplikacja sama
 * uruchamia odtwarzanie playlisty [playlistId] (null = wszystkie widoczne utwory).
 */
data class BtMode(
    val id: String,
    val name: String,
    val deviceAddress: String,
    val deviceName: String,
    val playlistId: String?,
    val shuffle: Boolean,
    val pauseOnDisconnect: Boolean,
    val enabled: Boolean,
)

/** Ostatnio odtwarzane – do wznawiania przyciskiem "play" w samochodzie. */
data class LastPlayback(
    val source: String,
    val trackKey: String?,
    val positionMs: Long,
)

data class StoreData(
    val playlists: List<Playlist> = emptyList(),
    val modes: List<BtMode> = emptyList(),
    /** Zaznaczone foldery. null = wszystkie foldery są widoczne. */
    val selectedFolders: Set<String>? = null,
    val last: LastPlayback? = null,
)

/** Źródła kolejki odtwarzania zapisywane w metadanych utworów. */
object Sources {
    const val ALL = "all"
    fun playlist(id: String) = "pl:$id"
    fun folder(id: String) = "folder:$id"
}
