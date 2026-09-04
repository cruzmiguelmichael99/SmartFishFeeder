package com.example.smartfishfeeder.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GeocodingRetrofitInstance {

    // Note: no "/data/2.5/" here, unlike RetrofitInstance (weather) —
    // geocoding lives at a different base path on the same OpenWeather host.
    private const val BASE_URL = "https://api.openweathermap.org/"

    val api: GeocodingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingApiService::class.java)
    }
}