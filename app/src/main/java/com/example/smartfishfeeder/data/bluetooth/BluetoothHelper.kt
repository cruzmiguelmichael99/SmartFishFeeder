package com.example.smartfishfeeder.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Sends a "FEED" command directly to the ESP32 over classic Bluetooth
 * (SPP), for when there's no internet and the normal Firestore-mediated
 * command path (DeviceRepository.sendFeedNowCommand) can't reach the
 * device. The ESP32 must already be paired via Android's system Bluetooth
 * settings and running a matching BluetoothSerial (SerialBT) listener on
 * its firmware — see the ESP32 firmware notes for that addition.
 */
class BluetoothHelper(private val context: Context) {

    companion object {
        // Standard Serial Port Profile UUID — works with ESP32's built-in
        // BluetoothSerial library (SerialBT) out of the box, no custom
        // GATT service needed.
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Must match whatever name the ESP32 advertises via
        // SerialBT.begin("FishFeeder-BT") in its firmware.
        private const val DEVICE_NAME = "FishFeeder-BT"
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Bluetooth was a normal (install-time) permission before Android 12
        }
    }

    /**
     * Connects to the paired ESP32 over classic Bluetooth and sends "FEED".
     * Returns true only on confirmed success, false on any failure (not
     * paired, Bluetooth off, out of range, permission missing, etc.) so
     * the caller can show an honest message instead of assuming it worked.
     */
    @Suppress("MissingPermission") // permission checked via hasBluetoothPermission() before any BT call
    suspend fun sendFeedCommand(): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermission()) return@withContext false

        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
        if (!adapter.isEnabled) return@withContext false

        val device = adapter.bondedDevices?.firstOrNull { it.name == DEVICE_NAME }
            ?: return@withContext false

        var socket: BluetoothSocket? = null
        return@withContext try {
            // NOTE: cancelDiscovery() was removed here. This app never
            // calls startDiscovery() (it only connects to a device the
            // user already paired via system Bluetooth settings), so
            // cancelDiscovery() had nothing to cancel. On Android 12+ it
            // also requires BLUETOOTH_SCAN, a permission this app doesn't
            // request, so calling it could throw a SecurityException and
            // crash this offline fallback path on newer phones.
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            socket.outputStream.write("FEED\n".toByteArray())
            socket.outputStream.flush()
            true
        } catch (e: IOException) {
            false
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                // ignore — best-effort cleanup
            }
        }
    }
}