@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myplayer.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myplayer.bluetooth.bondedDevices
import com.example.myplayer.data.Sources
import com.example.myplayer.util.Permissions

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Utwory", Icons.Filled.LibraryMusic),
    Tab("Playlisty", Icons.AutoMirrored.Filled.QueueMusic),
    Tab("Foldery", Icons.Filled.Folder),
    Tab("Auto", Icons.Filled.DirectionsCar),
)

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current

    val data by vm.data.collectAsStateWithLifecycle()
    val visibleTracks by vm.visibleTracks.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val trackIndex by vm.trackIndex.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val player by vm.player.state.collectAsStateWithLifecycle()

    // ---------- Uprawnienia ----------
    var audioGranted by remember { mutableStateOf(Permissions.hasAudio(context)) }
    var btGranted by remember { mutableStateOf(Permissions.hasBluetoothConnect(context)) }
    var notifGranted by remember { mutableStateOf(Permissions.hasNotifications(context)) }

    fun recheckPermissions() {
        val audioNow = Permissions.hasAudio(context)
        if (audioNow && !audioGranted) vm.refreshLibrary()
        audioGranted = audioNow
        btGranted = Permissions.hasBluetoothConnect(context)
        notifGranted = Permissions.hasNotifications(context)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { recheckPermissions() }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (!audioGranted) add(Permissions.audioPermission())
            if (Build.VERSION.SDK_INT >= 33 && !notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) permissionsLauncher.launch(needed.toTypedArray())
    }

    // Po powrocie z ustawień systemowych sprawdź uprawnienia ponownie
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { recheckPermissions() }

    val requestAudio = { permissionsLauncher.launch(arrayOf(Permissions.audioPermission())) }
    val requestBluetooth = {
        if (Build.VERSION.SDK_INT >= 31) {
            permissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    val openBatterySettings = {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // rzadkie urządzenia bez tego ekranu ustawień – nic się nie dzieje
        }
    }

    // ---------- Nawigacja ----------
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && tab == 1 && openPlaylistId != null) { openPlaylistId = null }

    val currentTrack = player.mediaId?.let { trackIndex[it] }

    if (showPlayer && player.hasMedia) {
        PlayerScreen(
            state = player,
            artworkUri = currentTrack?.uri,
            controller = vm.player,
            onClose = { showPlayer = false },
        )
    } else Scaffold(
        // Każdy ekran sam obsługuje górny pasek stanu (TopAppBar), więc Scaffold nie dodaje marginesów
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                if (player.hasMedia) {
                    MiniPlayer(
                        state = player,
                        artworkUri = currentTrack?.uri,
                        onOpen = { showPlayer = true },
                        onTogglePlay = vm.player::togglePlay,
                        onNext = vm.player::next,
                    )
                }
                NavigationBar {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> LibraryScreen(
                    tracks = visibleTracks,
                    permissionGranted = audioGranted,
                    loading = loading,
                    currentMediaId = player.mediaId,
                    playlists = data.playlists,
                    onRequestPermission = requestAudio,
                    onRefresh = vm::refreshLibrary,
                    onPlay = { queue, index -> vm.playTracks(queue, index, Sources.ALL) },
                    onAddToPlaylist = { playlist, keys -> vm.store.addToPlaylist(playlist.id, keys) },
                    onCreatePlaylist = { name, keys -> vm.store.createPlaylist(name, keys) },
                )

                1 -> {
                    val playlist = data.playlists.firstOrNull { it.id == openPlaylistId }
                    if (playlist != null) {
                        PlaylistDetailScreen(
                            playlist = playlist,
                            trackIndex = trackIndex,
                            currentMediaId = player.mediaId,
                            onBack = { openPlaylistId = null },
                            onPlayFrom = { queue, index ->
                                vm.playTracks(queue, index, Sources.playlist(playlist.id))
                            },
                            onShuffle = { vm.playSource(Sources.playlist(playlist.id), shuffle = true) },
                            onRemove = { vm.store.removeFromPlaylist(playlist.id, it) },
                            onMove = { from, to -> vm.store.moveInPlaylist(playlist.id, from, to) },
                        )
                    } else {
                        PlaylistsScreen(
                            playlists = data.playlists,
                            onOpen = { openPlaylistId = it.id },
                            onCreate = { vm.store.createPlaylist(it) },
                            onRename = { playlist, name -> vm.store.renamePlaylist(playlist.id, name) },
                            onDelete = { vm.store.deletePlaylist(it.id) },
                        )
                    }
                }

                2 -> FoldersScreen(
                    folders = folders,
                    selectedFolders = data.selectedFolders,
                    permissionGranted = audioGranted,
                    onToggle = vm::setFolderSelected,
                    onSelectAll = vm::selectAllFolders,
                    onSelectNone = vm::selectNoFolders,
                    onRefresh = vm::refreshLibrary,
                    onPlayFolder = { vm.playSource(Sources.folder(it.id), shuffle = false) },
                    onCreatePlaylist = { folder, name -> vm.createPlaylistFromFolder(folder.id, name) },
                    onRequestPermission = requestAudio,
                )

                else -> ModesScreen(
                    modes = data.modes,
                    playlists = data.playlists,
                    hasBluetoothPermission = btGranted,
                    hasNotificationPermission = notifGranted,
                    onRequestBluetooth = requestBluetooth,
                    onRequestNotifications = requestNotifications,
                    onOpenBatterySettings = openBatterySettings,
                    loadDevices = { bondedDevices(context) },
                    onSave = { vm.store.upsertMode(it) },
                    onDelete = { vm.store.deleteMode(it.id) },
                    onTest = { vm.testMode(it) },
                )
            }
        }
    }
}
