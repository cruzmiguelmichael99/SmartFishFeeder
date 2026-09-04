package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.BuildConfig
import com.example.smartfishfeeder.data.datasource.MockDataSource
import com.example.smartfishfeeder.data.model.WeatherData
import com.example.smartfishfeeder.data.remote.GeocodingRetrofitInstance
import com.example.smartfishfeeder.data.remote.RetrofitInstance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherRepository {

    // Kept as a starting value so the Dashboard has something to show
    // before the real network call finishes.
    fun getWeatherData(): WeatherData {
        return MockDataSource.weatherData
    }

    /**
     * Calls the real OpenWeather API. Returns null on any failure (no
     * internet, bad API key, etc.) so the caller can decide what to do,
     * e.g. keep showing the last known value instead of crashing.
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherData? {
        return try {
            val response = RetrofitInstance.api.getCurrentWeather(
                lat = latitude,
                lon = longitude,
                apiKey = BuildConfig.OPEN_WEATHER_API_KEY
            )

            WeatherData(
                temperature = response.main.temp,
                humidity = response.main.humidity,
                weatherCondition = response.weather.firstOrNull()?.main ?: "Unknown",
                precipitation = if (response.rain != null) "Rain expected" else "No rain expected",
                timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks the next ~6 hours of forecast (OpenWeather's forecast endpoint
     * returns data in 3-hour steps, so count=2 covers that window) for an
     * incoming storm. Returns the condition name and a rough "within X
     * hours" label for the first stormy entry found, or null if the next
     * 6 hours look clear (or the fetch failed). Used by StormCheckWorker
     * for the predictive/background storm alert.
     */
    suspend fun fetchIncomingStorm(latitude: Double, longitude: Double): Pair<String, String>? {
        return try {
            val response = RetrofitInstance.api.getForecast(
                lat = latitude,
                lon = longitude,
                apiKey = BuildConfig.OPEN_WEATHER_API_KEY,
                count = 2
            )

            val stormyConditions = listOf("Thunderstorm", "Rain")

            val hitIndex = response.list.indexOfFirst { item ->
                val condition = item.weather.firstOrNull()?.main
                condition != null && stormyConditions.any { it.equals(condition, ignoreCase = true) }
            }

            if (hitIndex == -1) return null

            val condition = response.list[hitIndex].weather.firstOrNull()?.main ?: return null
            val etaLabel = if (hitIndex == 0) "within 3 hours" else "within 6 hours"

            condition to etaLabel
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts a typed address/place name (e.g. "Calamba, Laguna") into
     * coordinates, using OpenWeather's Geocoding API — same API key as the
     * weather endpoint, no separate service needed. Returns the resolved
     * place name alongside the coordinates so the UI can show back
     * something like "Calamba, Laguna, PH" as confirmation, not just raw
     * numbers. Returns null if nothing matched or the call failed.
     */
    suspend fun geocodeAddress(address: String): GeocodedLocation? {
        return try {
            val results = GeocodingRetrofitInstance.api.geocode(
                query = address,
                limit = 1,
                apiKey = BuildConfig.OPEN_WEATHER_API_KEY
            )
            val first = results.firstOrNull() ?: return null

            val displayName = listOfNotNull(first.name, first.state, first.country)
                .joinToString(", ")

            GeocodedLocation(
                latitude = first.lat,
                longitude = first.lon,
                displayName = displayName
            )
        } catch (e: Exception) {
            null
        }
    }
}

data class GeocodedLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)