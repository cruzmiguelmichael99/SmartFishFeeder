package com.example.smartfishfeeder.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApiService {

    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<GeocodingResult>
}