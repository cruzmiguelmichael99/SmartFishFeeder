package com.example.smartfishfeeder.data.model

/** A single entry in the in-app notification list shown from the Dashboard's bell icon. */
data class AppNotification(
    val id: String = "",
    val message: String = "",
    val timestamp: String = ""
)