package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.data.model.DeviceInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's side of the app <-> ESP32 contract, mediated entirely through
 * Firestore (no direct connection between phone and device):
 *
 * - The ESP32 writes its own heartbeat to users/{uid}/device/status
 *   (isOnline, lastSeen, deviceId, waterTemperature, waterSensorOk,
 *   feedLevelLow). This class listens to that live.
 * - The app writes commands (e.g. "feed now") to
 *   users/{uid}/commands/{commandId} for the ESP32 to poll/listen for,
 *   execute, and mark completed.
 * - Feeding schedules already live at users/{uid}/schedules (see
 *   ScheduleRepository) — the ESP32 reads that collection directly, no
 *   separate sync needed.
 *
 * This is the app-side contract only. The ESP32 firmware is a separate
 * project that reads/writes these same Firestore paths using the user's
 * UID (shown in Settings as the pairing ID) hardcoded into its config.
 */
class DeviceRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun deviceDoc() =
        firestore.collection("users").document(auth.currentUser?.uid ?: "")
            .collection("device").document("status")

    private fun commandsCollection() =
        firestore.collection("users").document(auth.currentUser?.uid ?: "")
            .collection("commands")

    /**
     * Live device status, updating immediately whenever the ESP32 writes a
     * new heartbeat — no polling or manual refresh needed. Emits
     * DeviceInfo() (isOnline = false) if the device has never connected.
     */
    fun observeDeviceStatus(): Flow<DeviceInfo> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(DeviceInfo())
            close()
            return@callbackFlow
        }

        val registration = deviceDoc().addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(DeviceInfo())
                return@addSnapshotListener
            }
            trySend(
                DeviceInfo(
                    isOnline = snapshot.getBoolean("isOnline") ?: false,
                    lastSeen = snapshot.getString("lastSeen") ?: "",
                    deviceId = snapshot.getString("deviceId") ?: "",
                    waterTemperature = snapshot.getDouble("waterTemperature"),
                    waterSensorOk = snapshot.getBoolean("waterSensorOk") ?: true,
                    feedLevelLow = snapshot.getBoolean("feedLevelLow") ?: false
                )
            )
        }

        awaitClose { registration.remove() }
    }

    /**
     * Queues a "feed now" command for the ESP32 to pick up. The ESP32 is
     * expected to listen/poll this collection, execute pending commands,
     * and mark them completed.
     */
    suspend fun sendFeedNowCommand() {
        auth.currentUser ?: return
        val id = "CMD${System.currentTimeMillis()}"
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val command = mapOf(
            "type" to "feed_now",
            "status" to "pending",
            "createdAt" to timeFormat.format(Date())
        )
        commandsCollection().document(id).set(command).await()
    }

    /** The current user's UID — shown in Settings as the pairing ID for the ESP32 firmware. */
    fun getPairingId(): String? = auth.currentUser?.uid
}