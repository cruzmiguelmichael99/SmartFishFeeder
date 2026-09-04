package com.example.smartfishfeeder.data.model

data class AIRecommendation(
    val recommendation: String,
    val reason: String,
    val confidence: String,
    val waterTemperature: Double,
    val weatherCondition: String,
    val timestamp: String
)