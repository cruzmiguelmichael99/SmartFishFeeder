package com.example.smartfishfeeder.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenWeatherApiService {

    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse

    /** Forecast in 3-hour steps. cnt=2 returns the next ~6 hours. */
    @GET("forecast")
    suspend fun getForecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("cnt") count: Int = 2
    ): ForecastResponse
}