package com.example.smartfishfeeder.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface GeminiApiService {

    @Headers("Content-Type: application/json")
    @POST("v1beta/models/gemini-3.5-flash-lite:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}