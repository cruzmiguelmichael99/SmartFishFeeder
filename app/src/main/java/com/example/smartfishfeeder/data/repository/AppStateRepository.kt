package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.data.model.AIRecommendation
import com.example.smartfishfeeder.data.model.WeatherData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Caches small per-user state that doesn't warrant its own subcollection:
 * the last successfully fetched weather and AI recommendation (so a
 * relaunch with no internet shows real data instead of mock), the
 * schedule master toggle, the last known device location (so
 * StormCheckWorker can check forecasts in the background without needing
 * ACCESS_BACKGROUND_LOCATION), and an optional manually-set pond location.
 * Stored as fields directly on the user's document, not a subcollection,
 * since there's only ever one "latest" value to keep per field.
 *
 * Uses manual field mapping rather than Firestore's toObject() — WeatherData
 * and AIRecommendation don't have default values on every property, and
 * toObject() has been unreliable in this project without them.
 */
class AppStateRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun userDoc() =
        firestore.collection("users").document(auth.currentUser?.uid ?: "")

    suspend fun saveLastWeather(weather: WeatherData) {
        if (auth.currentUser == null) return
        val raw = mapOf(
            "temperature" to weather.temperature,
            "humidity" to weather.humidity,
            "weatherCondition" to weather.weatherCondition,
            "precipitation" to weather.precipitation,
            "timestamp" to weather.timestamp
        )
        userDoc().set(mapOf("lastWeather" to raw), SetOptions.merge()).await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getLastWeather(): WeatherData? {
        if (auth.currentUser == null) return null
        val snapshot = userDoc().get().await()
        val raw = snapshot.get("lastWeather") as? Map<String, Any> ?: return null
        return WeatherData(
            temperature = (raw["temperature"] as? Number)?.toDouble() ?: return null,
            humidity = (raw["humidity"] as? Number)?.toInt() ?: 0,
            weatherCondition = raw["weatherCondition"] as? String ?: "",
            precipitation = raw["precipitation"] as? String ?: "",
            timestamp = raw["timestamp"] as? String ?: ""
        )
    }

    suspend fun saveLastAIRecommendation(recommendation: AIRecommendation) {
        if (auth.currentUser == null) return
        val raw = mapOf(
            "recommendation" to recommendation.recommendation,
            "reason" to recommendation.reason,
            "confidence" to recommendation.confidence,
            "waterTemperature" to recommendation.waterTemperature,
            "weatherCondition" to recommendation.weatherCondition,
            "timestamp" to recommendation.timestamp
        )
        userDoc().set(mapOf("lastAIRecommendation" to raw), SetOptions.merge()).await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getLastAIRecommendation(): AIRecommendation? {
        if (auth.currentUser == null) return null
        val snapshot = userDoc().get().await()
        val raw = snapshot.get("lastAIRecommendation") as? Map<String, Any> ?: return null
        return AIRecommendation(
            recommendation = raw["recommendation"] as? String ?: return null,
            reason = raw["reason"] as? String ?: "",
            confidence = raw["confidence"] as? String ?: "Medium",
            waterTemperature = (raw["waterTemperature"] as? Number)?.toDouble() ?: 0.0,
            weatherCondition = raw["weatherCondition"] as? String ?: "",
            timestamp = raw["timestamp"] as? String ?: ""
        )
    }

    /** The master "Enable Schedule" toggle on the Schedule tab. */
    suspend fun saveScheduleEnabled(enabled: Boolean) {
        if (auth.currentUser == null) return
        userDoc().set(mapOf("scheduleEnabled" to enabled), SetOptions.merge()).await()
    }

    /** Returns null if never set before (so the caller can fall back to a sensible default). */
    suspend fun getScheduleEnabled(): Boolean? {
        if (auth.currentUser == null) return null
        val snapshot = userDoc().get().await()
        return snapshot.getBoolean("scheduleEnabled")
    }

    /** Saved after every successful foreground GPS fetch, for StormCheckWorker to reuse. */
    suspend fun saveLastLocation(latitude: Double, longitude: Double) {
        if (auth.currentUser == null) return
        val raw = mapOf("lat" to latitude, "lon" to longitude)
        userDoc().set(mapOf("lastLocation" to raw), SetOptions.merge()).await()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getLastLocation(): Pair<Double, Double>? {
        if (auth.currentUser == null) return null
        val snapshot = userDoc().get().await()
        val raw = snapshot.get("lastLocation") as? Map<String, Any> ?: return null
        val lat = (raw["lat"] as? Number)?.toDouble() ?: return null
        val lon = (raw["lon"] as? Number)?.toDouble() ?: return null
        return lat to lon
    }

    /**
     * A manually-set pond location, used instead of the phone's live GPS
     * whenever enabled = true — for when the pond isn't wherever the phone
     * happens to be. Kept separate from lastLocation (the phone's actual
     * GPS cache for StormCheckWorker) so switching this off doesn't lose
     * the coordinates the user typed in. displayName is the resolved
     * place name (e.g. "Calamba, Laguna, PH") shown back in Settings as
     * confirmation, not just raw coordinates.
     */
    suspend fun saveCustomLocation(enabled: Boolean, latitude: Double, longitude: Double, displayName: String) {
        if (auth.currentUser == null) return
        val raw = mapOf(
            "enabled" to enabled,
            "lat" to latitude,
            "lon" to longitude,
            "displayName" to displayName
        )
        userDoc().set(mapOf("customLocation" to raw), SetOptions.merge()).await()
    }

    /** Returns the saved custom location, or null if never set. */
    @Suppress("UNCHECKED_CAST")
    suspend fun getCustomLocation(): CustomLocation? {
        if (auth.currentUser == null) return null
        val snapshot = userDoc().get().await()
        val raw = snapshot.get("customLocation") as? Map<String, Any> ?: return null
        return CustomLocation(
            enabled = raw["enabled"] as? Boolean ?: false,
            latitude = (raw["lat"] as? Number)?.toDouble() ?: 0.0,
            longitude = (raw["lon"] as? Number)?.toDouble() ?: 0.0,
            displayName = raw["displayName"] as? String ?: ""
        )
    }

    /** Saves the newest notification the user has already seen. */
    suspend fun saveLastSeenNotificationId(notificationId: String) {
        if (auth.currentUser == null) return

        userDoc()
            .set(
                mapOf("lastSeenNotificationId" to notificationId),
                SetOptions.merge()
            )
            .await()
    }

    /** Returns the newest notification ID the user has already seen. */
    suspend fun getLastSeenNotificationId(): String? {
        if (auth.currentUser == null) return null

        val snapshot = userDoc().get().await()
        return snapshot.getString("lastSeenNotificationId")
    }
}

data class CustomLocation(
    val enabled: Boolean,
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)