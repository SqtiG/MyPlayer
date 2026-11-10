@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.myplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myplayer.data.Playlist
import com.example.myplayer.data.Track

/** Lista playlist + tworzenie nowej. */
@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    onOpen: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Playlist, String) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Playlisty") })
            if (playlists.isEmpty()) {
                CenteredMessage(
                    "Nie masz jeszcze playlist.\n\nUtwórz playlistę tutaj albo zaznacz utwory w zakładce " +
                        "„Utwory” (przytrzymaj utwór) i dodaj je do playlisty.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(playlists, key = { it.id }) { playlist ->
                        var menuOpen by remember { mutableStateOf(false) }
                        ListItem(
                            modifier = Modifier.clickable { onOpen(playlist) },
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                            },
                            headlineContent = {
                                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text("${playlist.trackKeys.size} utworów") },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Zmień nazwę") },
                                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                renaming = playlist
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Usuń") },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                deleting = playlist
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nowa playlista") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (creating) {
        NameDialog(
            title = "Nowa playlista",
            confirmText = "Utwórz",
            onConfirm = {
                onCreate(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { playlist ->
        NameDialog(
            title = "Zmień nazwę",
            initial = playlist.name,
            onConfirm = {
                onRename(playlist, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { playlist ->
        ConfirmDialog(
            title = "Usunąć playlistę?",
            message = "Playlista „${playlist.name}” zostanie usunięta. Same pliki muzyczne zostaną na telefonie.",
            onConfirm = {
                onDelete(playlist)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Zawartość jednej playlisty: odtwarzanie, usuwanie i zmiana kolejności utworów. */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    trackIndex: Map<String, Track>,
    currentMediaId: String?,
    onBack: () -> Unit,
    onPlayFrom: (queue: List<Track>, index: Int) -> Unit,
    onShuffle: () -> Unit,
    onRemove: (index: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    // Kolejka do odtworzenia = tylko utwory, które istnieją i są w widocznych folderach.
    val available = remember(playlist, trackIndex) { playlist.trackKeys.mapNotNull { trackIndex[it] } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                }
            },
            actions = {
                IconButton(onClick = { onPlayFrom(available, 0) }, enabled = available.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Odtwarzaj")
                }
                IconButton(onClick = onShuffle, enabled = available.isNotEmpty()) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Losowo")
                }
            },
        )

        if (playlist.trackKeys.isEmpty()) {
            CenteredMessage(
                "Ta playlista jest pusta.\n\nWejdź do zakładki „Utwory”, przytrzymaj utwory, " +
                    "zaznacz je i wybierz „Dodaj do playlisty”.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(playlist.trackKeys) { index, key ->
                    val track = trackIndex[key]
                    var menuOpen by remember { mutableStateOf(false) }

                    val menu: @Composable () -> Unit = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Przesuń w górę") },
                                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                                    enabled = index > 0,
                                    onClick = {
                                        menuOpen = false
                                        onMove(index, index - 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Przesuń w dół") },
                                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                                    enabled = index < playlist.trackKeys.lastIndex,
                                    onClick = {
                                        menuOpen = false
                                        onMove(index, index + 1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Usuń z playlisty") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onRemove(index)
                                    },
                                )
                            }
                        }
                    }

                    if (track != null) {
                        val isCurrent = track.key == currentMediaId
                        ListItem(
                            modifier = Modifier.clickable {
                                onPlayFrom(available, available.indexOfFirst { it.key == track.key }.coerceAtLeast(0))
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isCurrent) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            leadingContent = { Artwork(uri = track.uri, size = 48.dp) },
                            headlineContent = {
                                Text(
                                    track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        androidx.compose.ui.graphics.Color.Unspecified
                                    },
                                )
                            },
                            supportingContent = {
                                Text(
                                    track.artist.ifBlank { track.folderLabel },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatDuration(track.durationMs), style = MaterialTheme.typography.bodySmall)
                                    menu()
                                }
                            },
                        )
                    } else {
                        // Plik usunięty z telefonu albo jego folder jest odznaczony w zakładce "Foldery".
                        ListItem(
                            headlineContent = {
                                Text(
                                    key.substringAfterLast('/'),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            supportingContent = { Text("Niedostępny (brak pliku lub folder ukryty)") },
                            trailingContent = { menu() },
                        )
                    }
                }
            }
        }
    }
}
