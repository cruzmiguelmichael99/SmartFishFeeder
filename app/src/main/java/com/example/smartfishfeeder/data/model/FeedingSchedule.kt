package com.example.smartfishfeeder.data.model

data class FeedingSchedule(
    val id: String = "",
    val feedingTime: String = "",
    val enabled: Boolean = true,
    val feedingType: String = ""
)