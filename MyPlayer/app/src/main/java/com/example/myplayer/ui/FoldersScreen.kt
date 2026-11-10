@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myplayer.data.FolderInfo

/**
 * Wybór folderów, z których aplikacja pokazuje muzykę.
 * Odznaczony folder znika z biblioteki i z odtwarzania "wszystkich utworów" (playlisty też go pomijają),
 * ale pliki zostają nietknięte.
 */
@Composable
fun FoldersScreen(
    folders: List<FolderInfo>,
    selectedFolders: Set<String>?,
    permissionGranted: Boolean,
    onToggle: (folderId: String, selected: Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onRefresh: () -> Unit,
    onPlayFolder: (FolderInfo) -> Unit,
    onCreatePlaylist: (FolderInfo, name: String) -> Unit,
    onRequestPermission: () -> Unit,
) {
    var playlistFor by remember { mutableStateOf<FolderInfo?>(null) }
    val selectedCount = folders.count { selectedFolders == null || it.id in selectedFolders }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Foldery") },
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Odśwież")
                }
            },
        )

        if (!permissionGranted) {
            CenteredMessage("Brak dostępu do plików audio.") {
                TextButton(onClick = onRequestPermission) { Text("Zezwól na dostęp") }
            }
            return@Column
        }

        if (folders.isEmpty()) {
            CenteredMessage("Nie znaleziono folderów z plikami audio.")
            return@Column
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Zaznacz foldery, z których mają być wyświetlane utwory " +
                    "($selectedCount z ${folders.size}).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row {
                TextButton(onClick = onSelectAll) { Text("Zaznacz wszystkie") }
                TextButton(onClick = onSelectNone) { Text("Odznacz wszystkie") }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(folders, key = { it.id }) { folder ->
                val checked = selectedFolders == null || folder.id in selectedFolders
                var menuOpen by remember { mutableStateOf(false) }
                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(folder.id, !checked) },
                    leadingContent = {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(folder.id, it) })
                    },
                    headlineContent = {
                        Text(folder.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("${folder.trackCount} utworów") },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Odtwórz folder") },
                                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onPlayFolder(folder)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Utwórz playlistę z folderu") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        playlistFor = folder
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    playlistFor?.let { folder ->
        NameDialog(
            title = "Playlista z folderu",
            initial = folder.label.trimEnd('/').substringAfterLast('/'),
            confirmText = "Utwórz",
            onConfirm = { name ->
                onCreatePlaylist(folder, name)
                playlistFor = null
            },
            onDismiss = { playlistFor = null },
        )
    }
}
