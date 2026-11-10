package com.example.myplayer.data

/** Zwraca tylko utwory z zaznaczonych folderów (null = wszystkie). */
fun List<Track>.visibleIn(selectedFolders: Set<String>?): List<Track> =
    if (selectedFolders == null) this else filter { it.folderId in selectedFolders }

fun buildFolders(tracks: List<Track>): List<FolderInfo> =
    tracks.groupBy { it.folderId }
        .map { (id, list) -> FolderInfo(id = id, label = list.first().folderLabel, trackCount = list.size) }
        .sortedBy { it.label.lowercase() }

/**
 * Zamienia opis źródła na listę utworów.
 *  - "all"       -> wszystkie widoczne utwory
 *  - "pl:<id>"   -> utwory playlisty (tylko te, które są widoczne i istnieją)
 *  - "folder:<id>" -> wszystkie utwory z folderu (niezależnie od zaznaczenia)
 */
fun resolveSource(
    source: String,
    all: List<Track>,
    visible: List<Track>,
    playlists: List<Playlist>,
): List<Track> = when {
    source.startsWith("pl:") -> {
        val id = source.removePrefix("pl:")
        val byKey = visible.associateBy { it.key }
        playlists.firstOrNull { it.id == id }?.trackKeys?.mapNotNull { byKey[it] }.orEmpty()
    }
    source.startsWith("folder:") -> {
        val id = source.removePrefix("folder:")
        all.filter { it.folderId == id }
    }
    else -> visible
}
