package com.example.smartfishfeeder.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object GeminiRetrofitInstance {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val api: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApiService::class.java)
    }
}