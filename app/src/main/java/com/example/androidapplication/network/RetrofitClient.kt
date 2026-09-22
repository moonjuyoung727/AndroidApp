package com.example.androidapplication.network

import com.example.androidapplication.network.AuthApi

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {

    private const val BASE_URL = "http://example.com/"

    private val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val authApi: AuthApi =
        retrofit.create(
            AuthApi::class.java
        )

    val eventApi: EventApi =
        retrofit.create(
            EventApi::class.java
        )
}