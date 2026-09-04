package com.example.smartfishfeeder.data.remote

import com.google.gson.annotations.SerializedName

data class ForecastResponse(
    val list: List<ForecastItem>
)

data class ForecastItem(
    @SerializedName("dt_txt") val dateTimeText: String,
    val weather: List<WeatherInfo>
)