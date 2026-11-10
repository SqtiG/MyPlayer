package com.example.myplayer.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.myplayer.MusicApp
import com.example.myplayer.data.BtMode
import com.example.myplayer.playback.PlaybackService
import com.example.myplayer.util.Permissions

/**
 * Odbiornik zdarzeń Bluetooth zadeklarowany w manifeście – działa także wtedy, gdy aplikacja
 * jest zamknięta. Po połączeniu ze znanym urządzeniem uruchamia usługę odtwarzania.
 *
 * Wymaga przyznanego uprawnienia BLUETOOTH_CONNECT (Android 12+), inaczej system nie dostarcza
 * tych broadcastów.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val address = intent.bluetoothDevice()?.address ?: return
        val container = (context.applicationContext as? MusicApp)?.container ?: return
        val modes = container.store.data.value.modes
            .filter { it.deviceAddress.equals(address, ignoreCase = true) }
        if (modes.isEmpty()) return

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val mode = modes.firstOrNull { it.enabled } ?: return
                if (isDuplicate(address)) return
                Log.i(TAG, "Połączono z ${mode.deviceName} – uruchamiam tryb ${mode.name}")
                launchMode(context, mode)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                lastConnect.remove(address.uppercase())
                PlaybackService.instance?.onBluetoothDisconnected(address)
            }
        }
    }

    private fun launchMode(context: Context, mode: BtMode) {
        if (!Permissions.hasAudio(context)) {
            Log.w(TAG, "Brak uprawnienia do muzyki – pomijam")
            return
        }
        try {
            ContextCompat.startForegroundService(context, PlaybackService.startIntent(context, mode.id))
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException i podobne – plan B: powiadomienie
            Log.w(TAG, "Start usługi z tła zablokowany, pokazuję powiadomienie", e)
            BtNotifications.showTapToPlay(context, mode)
        }
    }

    /** Jedno połączenie potrafi wysłać kilka broadcastów (klasyczny + BLE) – ignorujemy powtórki. */
    private fun isDuplicate(address: String): Boolean {
        val key = address.uppercase()
        val now = SystemClock.elapsedRealtime()
        val previous = lastConnect[key]
        lastConnect[key] = now
        return previous != null && now - previous < 5_000
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private companion object {
        const val TAG = "BluetoothReceiver"
        val lastConnect = HashMap<String, Long>()
    }
}
