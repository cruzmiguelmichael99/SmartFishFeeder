package com.example.smartfishfeeder.data.repository

import com.example.smartfishfeeder.BuildConfig
import com.example.smartfishfeeder.data.datasource.MockDataSource
import com.example.smartfishfeeder.data.model.AIRecommendation
import com.example.smartfishfeeder.data.model.FishSpecies
import com.example.smartfishfeeder.data.remote.GeminiContent
import com.example.smartfishfeeder.data.remote.GeminiPart
import com.example.smartfishfeeder.data.remote.GeminiRequest
import com.example.smartfishfeeder.data.remote.GeminiRetrofitInstance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecommendationRepository {

    // Kept as a starting value until the real Gemini call succeeds.
    fun getAIRecommendation(): AIRecommendation {
        return MockDataSource.aiRecommendation
    }

    /**
     * Sends the current sensor and weather readings, plus the selected fish
     * species and its ideal water temperature range, to Gemini and asks for
     * a feeding recommendation. Returns null on any failure (no internet,
     * bad key, unexpected response shape, etc.) so the caller can decide
     * what to do, e.g. keep showing the last known recommendation.
     *
     * waterSensorOk distinguishes a live water temperature reading from a
     * preserved-but-stale one (the ESP32 keeps the last good value in
     * Firestore when a sensor read fails, instead of clearing it), so
     * Gemini's confidence reflects data freshness honestly.
     */
    suspend fun fetchAIRecommendation(
        waterTemperature: Double?,
        waterSensorOk: Boolean,
        airTemperature: Double,
        humidity: Int,
        weatherCondition: String,
        species: FishSpecies
    ): AIRecommendation? {
        val waterTempLine = when {
            waterTemperature == null ->
                "- Water temperature: Not available (the pond sensor hasn't connected yet)"
            !waterSensorOk ->
                "- Water temperature: $waterTemperature°C (last known reading, sensor is currently not responding)"
            else ->
                "- Water temperature: $waterTemperature°C"
        }

        val confidenceGuidance = if (waterTemperature != null && waterSensorOk) {
            "Also estimate your confidence in this recommendation as Low, Medium, or High."
        } else {
            "Since water temperature isn't currently available or reliable, base your " +
                    "recommendation mainly on air temperature and weather, and set your " +
                    "confidence no higher than Medium, since a key data point for " +
                    "${species.displayName} is missing or stale."
        }

        val prompt = """
            You are an assistant for a smart fish pond feeder raising ${species.displayName}.
            The ideal water temperature range for ${species.displayName} is ${species.minIdealTemp}°C to ${species.maxIdealTemp}°C.
            Base your recommendation on the known temperature tolerance and feeding
            behavior of this species specifically, not generic fish. Current data:
            $waterTempLine
            - Air temperature: $airTemperature°C
            - Humidity: $humidity%
            - Weather condition: $weatherCondition

            Decide whether to feed the fish normally, reduce feeding, or skip feeding,
            taking into account whether the water temperature is inside or outside the
            ideal range for ${species.displayName}, when that data is available and reliable.
            $confidenceGuidance
            Respond in exactly this format, with no extra text before or after:
            RECOMMENDATION: <a short 2-4 word recommendation, e.g. "Feed Normally">
            CONFIDENCE: <Low, Medium, or High>
            REASON: <one short sentence explaining why, based on the data above>
        """.trimIndent()

        return try {
            val response = GeminiRetrofitInstance.api.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    )
                )
            )

            val replyText = response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: return null


            val recommendationLine = replyText.lineSequence()
                .firstOrNull { it.startsWith("RECOMMENDATION:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()

            val reasonLine = replyText.lineSequence()
                .firstOrNull { it.startsWith("REASON:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()

            val confidenceLine = replyText.lineSequence()
                .firstOrNull { it.startsWith("CONFIDENCE:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()

            if (recommendationLine == null || reasonLine == null) return null

            AIRecommendation(
                recommendation = recommendationLine,
                reason = reasonLine,
                confidence = confidenceLine ?: "Medium",
                waterTemperature = waterTemperature ?: 0.0,
                weatherCondition = weatherCondition,
                timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            )
        } catch (e: Exception) {

            null
        }
    }
}