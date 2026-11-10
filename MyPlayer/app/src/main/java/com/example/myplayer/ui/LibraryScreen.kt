@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myplayer.data.Playlist
import com.example.myplayer.data.Track

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    permissionGranted: Boolean,
    loading: Boolean,
    currentMediaId: String?,
    playlists: List<Playlist>,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (queue: List<Track>, index: Int) -> Unit,
    onAddToPlaylist: (Playlist, List<String>) -> Unit,
    onCreatePlaylist: (name: String, keys: List<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pendingAdd by remember { mutableStateOf<List<String>?>(null) }
    val selecting = selected.isNotEmpty()

    val filtered = remember(tracks, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            tracks
        } else {
            tracks.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
    }

    BackHandler(enabled = selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (selecting) "Zaznaczono: ${selected.size}" else "Utwory (${tracks.size})") },
            navigationIcon = {
                if (selecting) {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Anuluj zaznaczanie")
                    }
                }
            },
            actions = {
                if (selecting) {
                    IconButton(onClick = { selected = filtered.map { it.key }.toSet() }) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "Zaznacz wszystko")
                    }
                    IconButton(onClick = { pendingAdd = filtered.map { it.key }.filter { it in selected } }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Dodaj do playlisty")
                    }
                } else if (permissionGranted) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                    }
                }
            },
        )

        when {
            !permissionGranted -> CenteredMessage(
                text = "Aby wyświetlić muzykę z telefonu, aplikacja potrzebuje dostępu do plików audio.",
            ) {
                Button(onClick = onRequestPermission) { Text("Zezwól na dostęp") }
            }

            loading && tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Szukaj utworu, wykonawcy, albumu") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (filtered.isEmpty()) {
                    CenteredMessage(
                        text = if (tracks.isEmpty()) {
                            "Brak utworów. Sprawdź, które foldery są zaznaczone w zakładce „Foldery”."
                        } else {
                            "Nic nie znaleziono."
                        },
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(filtered, key = { _, track -> track.key }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = track.key == currentMediaId,
                                selecting = selecting,
                                isSelected = track.key in selected,
                                onClick = {
                                    if (selecting) {
                                        selected = if (track.key in selected) selected - track.key else selected + track.key
                                    } else {
                                        onPlay(filtered, index)
                                    }
                                },
                                onLongClick = { selected = selected + track.key },
                                menuItems = { close ->
                                    DropdownMenuItem(
                                        text = { Text("Dodaj do playlisty") },
                                        onClick = {
                                            close()
                                            pendingAdd = listOf(track.key)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Zaznacz wiele") },
                                        onClick = {
                                            close()
                                            selected = selected + track.key
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingAdd?.let { keys ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = {
                onAddToPlaylist(it, keys)
                pendingAdd = null
                selected = emptySet()
            },
            onCreate = { name ->
                onCreatePlaylist(name, keys)
                pendingAdd = null
                selected = emptySet()
            },
            onDismiss = { pendingAdd = null },
        )
    }
}

@Composable
fun CenteredMessage(text: String, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, textAlign = TextAlign.Center)
        if (action != null) {
            Box(Modifier.padding(top = 16.dp)) { action() }
        }
    }
}
