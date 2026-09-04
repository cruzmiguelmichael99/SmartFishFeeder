package com.example.smartfishfeeder.data.model

data class DeviceStatus(
    val deviceId: String,
    val deviceName: String,
    val isOnline: Boolean,
    val wifiConnected: Boolean,
    val temperatureSensorNormal: Boolean,
    val feederReady: Boolean,
    val lastCommunicationTime: String
)