package com.example.smartfishfeeder.data.remote

import com.google.gson.annotations.SerializedName

data class OpenWeatherResponse(
    val main: MainInfo,
    val weather: List<WeatherInfo>,
    val rain: RainInfo? = null
)

data class MainInfo(
    val temp: Double,
    val humidity: Int
)

data class WeatherInfo(
    val main: String,
    val description: String
)

data class RainInfo(
    @SerializedName("1h") val oneHour: Double? = null
)