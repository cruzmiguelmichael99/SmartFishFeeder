package com.example.smartfishfeeder.data.model

data class WeatherData(
    val temperature: Double,
    val humidity: Int,
    val weatherCondition: String,
    val precipitation: String,
    val timestamp: String
)