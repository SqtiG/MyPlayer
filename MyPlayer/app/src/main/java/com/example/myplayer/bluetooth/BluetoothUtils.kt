package com.example.myplayer.bluetooth

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myplayer.Channels
import com.example.myplayer.R
import com.example.myplayer.data.BtMode
import com.example.myplayer.playback.PlaybackService
import com.example.myplayer.util.Permissions

data class BtDeviceInfo(val name: String, val address: String)

/** Lista sparowanych urządzeń Bluetooth (wymaga uprawnienia BLUETOOTH_CONNECT). */
@SuppressLint("MissingPermission")
fun bondedDevices(context: Context): List<BtDeviceInfo> {
    if (!Permissions.hasBluetoothConnect(context)) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices
            ?.map { BtDeviceInfo(name = it.name ?: it.address, address = it.address) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
    } catch (e: SecurityException) {
        emptyList()
    }
}

object BtNotifications {

    private const val ID_BASE = 2000

    /**
     * Plan B: gdy system nie pozwoli uruchomić odtwarzania z tła, pokazujemy powiadomienie.
     * Dotknięcie powiadomienia to akcja użytkownika, więc system zezwala wtedy na start odtwarzania.
     */
    @SuppressLint("MissingPermission")
    fun showTapToPlay(context: Context, mode: BtMode) {
        if (!Permissions.hasNotifications(context)) return
        val pending = PendingIntent.getForegroundService(
            context,
            mode.id.hashCode(),
            PlaybackService.startIntent(context, mode.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Channels.PROMPT)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle("Połączono: ${mode.deviceName}")
            .setContentText("Dotknij, aby odtworzyć – tryb „${mode.name}”")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        NotificationManagerCompat.from(context).notify(ID_BASE + (mode.id.hashCode() and 0xFFF), notification)
    }
}
