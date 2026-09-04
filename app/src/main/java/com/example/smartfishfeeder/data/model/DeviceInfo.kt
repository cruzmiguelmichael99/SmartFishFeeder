package com.example.smartfishfeeder.data.model

/**
 * Live status of the ESP32 feeder, written by the ESP32 itself as a
 * heartbeat and read live by the app. isOnline = false / empty fields
 * means the device has never connected, or Firestore has nothing yet.
 *
 * waterTemperature is null until the ESP32 has sent at least one real
 * DS18B20 reading — the UI should show a "no data yet" state for null,
 * not 0.0°C, since 0.0 is a real (if unlikely) temperature.
 *
 * waterSensorOk reflects the sensor's status as of the MOST RECENT
 * heartbeat, independent of waterTemperature. The ESP32 keeps the last
 * good waterTemperature value in Firestore when a read fails (instead of
 * clearing it), so waterSensorOk is what tells the UI whether that number
 * is live or stale. Defaults to true so a device that's never connected
 * doesn't show a false sensor warning — "No data" from a null
 * waterTemperature already covers that case.
 */
data class DeviceInfo(
    val isOnline: Boolean = false,
    val lastSeen: String = "",
    val deviceId: String = "",
    val waterTemperature: Double? = null,
    val waterSensorOk: Boolean = true,
    val feedLevelLow: Boolean = false
)