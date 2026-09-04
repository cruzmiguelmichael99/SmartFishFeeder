package com.example.smartfishfeeder.data.model

data class FeedingEvent(
    val id: String = "",
    val dateTime: String = "",
    val automatic: Boolean = false,
    val status: String = ""
)