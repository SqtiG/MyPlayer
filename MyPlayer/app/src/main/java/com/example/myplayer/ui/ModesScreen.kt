@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.myplayer.bluetooth.BtDeviceInfo
import com.example.myplayer.data.BtMode
import com.example.myplayer.data.Playlist
import java.util.UUID

/**
 * Tryby Bluetooth: "gdy połączę się z tym urządzeniem – odtwórz tę playlistę".
 */
@Composable
fun ModesScreen(
    modes: List<BtMode>,
    playlists: List<Playlist>,
    hasBluetoothPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    loadDevices: () -> List<BtDeviceInfo>,
    onSave: (BtMode) -> Unit,
    onDelete: (BtMode) -> Unit,
    onTest: (BtMode) -> Unit,
) {
    // null = okno zamknięte; BtMode z pustym id = nowy tryb
    var editing by remember { mutableStateOf<BtMode?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Tryby Bluetooth") })

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    RequirementsCard(
                        hasBluetoothPermission = hasBluetoothPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestBluetooth = onRequestBluetooth,
                        onRequestNotifications = onRequestNotifications,
                        onOpenBatterySettings = onOpenBatterySettings,
                    )
                }

                if (modes.isEmpty()) {
                    item {
                        Text(
                            "Nie masz jeszcze żadnego trybu. Dodaj np. tryb „Samochód”: wybierz zestaw " +
                                "głośnomówiący auta i playlistę – po połączeniu muzyka włączy się sama.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                items(modes, key = { it.id }) { mode ->
                    val playlistName = mode.playlistId
                        ?.let { id -> playlists.firstOrNull { it.id == id }?.name }
                        ?: "Wszystkie utwory"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = mode },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.DirectionsCar, contentDescription = null)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp),
                            ) {
                                Text(
                                    mode.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Urządzenie: ${mode.deviceName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Playlista: $playlistName" + if (mode.shuffle) " (losowo)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onTest(mode) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Testuj tryb")
                            }
                            Switch(
                                checked = mode.enabled,
                                onCheckedChange = { onSave(mode.copy(enabled = it)) },
                            )
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                editing = BtMode(
                    id = "",
                    name = "",
                    deviceAddress = "",
                    deviceName = "",
                    playlistId = null,
                    shuffle = false,
                    pauseOnDisconnect = true,
                    enabled = true,
                )
            },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nowy tryb") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    editing?.let { mode ->
        ModeEditDialog(
            initial = mode,
            playlists = playlists,
            devices = remember(mode.id, hasBluetoothPermission) { loadDevices() },
            hasBluetoothPermission = hasBluetoothPermission,
            onRequestBluetooth = onRequestBluetooth,
            onSave = {
                onSave(if (it.id.isEmpty()) it.copy(id = UUID.randomUUID().toString()) else it)
                editing = null
            },
            onDelete = if (mode.id.isNotEmpty()) {
                {
                    onDelete(mode)
                    editing = null
                }
            } else {
                null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RequirementsCard(
    hasBluetoothPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aby autostart działał", style = MaterialTheme.typography.titleSmall)
            RequirementRow(
                ok = hasBluetoothPermission,
                text = "Uprawnienie Bluetooth (dla rozpoznania połączenia z autem)",
                actionLabel = "Zezwól",
                onAction = onRequestBluetooth,
            )
            RequirementRow(
                ok = hasNotificationPermission,
                text = "Powiadomienia (pokazują odtwarzacz i plan B po połączeniu)",
                actionLabel = "Zezwól",
                onAction = onRequestNotifications,
            )
            RequirementRow(
                ok = null,
                text = "Zalecane: wyłącz optymalizację baterii dla tej aplikacji – system chętniej " +
                    "pozwala wtedy startować odtwarzanie w tle.",
                actionLabel = "Ustawienia",
                onAction = onOpenBatterySettings,
            )
        }
    }
}

@Composable
private fun RequirementRow(ok: Boolean?, text: String, actionLabel: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (ok) {
            true -> Icon(Icons.Filled.Check, contentDescription = "OK")
            false -> Icon(Icons.Filled.Warning, contentDescription = "Brak")
            null -> Icon(Icons.Filled.Warning, contentDescription = "Zalecane")
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        if (ok != true) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ModeEditDialog(
    initial: BtMode,
    playlists: List<Playlist>,
    devices: List<BtDeviceInfo>,
    hasBluetoothPermission: Boolean,
    onRequestBluetooth: () -> Unit,
    onSave: (BtMode) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var deviceAddress by remember { mutableStateOf(initial.deviceAddress) }
    var deviceName by remember { mutableStateOf(initial.deviceName) }
    var playlistId by remember { mutableStateOf(initial.playlistId) }
    var shuffle by remember { mutableStateOf(initial.shuffle) }
    var pauseOnDisconnect by remember { mutableStateOf(initial.pauseOnDisconnect) }
    var enabled by remember { mutableStateOf(initial.enabled) }

    var pickingDevice by remember { mutableStateOf(false) }
    var pickingPlaylist by remember { mutableStateOf(false) }

    val playlistLabel = playlistId
        ?.let { id -> playlists.firstOrNull { it.id == id }?.name }
        ?: "Wszystkie utwory"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isEmpty()) "Nowy tryb" else "Edytuj tryb") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nazwa (np. Samochód)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!hasBluetoothPermission) {
                    Text(
                        "Do wyboru urządzenia potrzebne jest uprawnienie Bluetooth.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onRequestBluetooth) { Text("Zezwól na Bluetooth") }
                } else {
                    OutlinedButton(
                        onClick = { pickingDevice = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (deviceAddress.isEmpty()) "Wybierz urządzenie Bluetooth" else "Urządzenie: $deviceName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (devices.isEmpty()) {
                        Text(
                            "Brak sparowanych urządzeń. Najpierw sparuj telefon z samochodem w ustawieniach Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                OutlinedButton(
                    onClick = { pickingPlaylist = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Playlista: $playlistLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                SwitchRow("Odtwarzaj losowo", shuffle) { shuffle = it }
                SwitchRow("Pauzuj po rozłączeniu", pauseOnDisconnect) { pauseOnDisconnect = it }
                SwitchRow("Uruchamiaj automatycznie", enabled) { enabled = it }

                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Usuń ten tryb") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && deviceAddress.isNotEmpty(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            deviceAddress = deviceAddress,
                            deviceName = deviceName,
                            playlistId = playlistId,
                            shuffle = shuffle,
                            pauseOnDisconnect = pauseOnDisconnect,
                            enabled = enabled,
                        )
                    )
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )

    if (pickingDevice) {
        ChoiceDialog(
            title = "Urządzenie Bluetooth",
            options = devices,
            selected = devices.firstOrNull { it.address == deviceAddress },
            label = { "${it.name}  (${it.address})" },
            onSelect = {
                deviceAddress = it.address
                deviceName = it.name
                if (name.isBlank()) name = it.name
            },
            onDismiss = { pickingDevice = false },
        )
    }
    if (pickingPlaylist) {
        val options: List<Playlist?> = listOf<Playlist?>(null) + playlists
        ChoiceDialog(
            title = "Playlista",
            options = options,
            selected = playlists.firstOrNull { it.id == playlistId },
            label = { it?.name ?: "Wszystkie utwory" },
            onSelect = { playlistId = it?.id },
            onDismiss = { pickingPlaylist = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
