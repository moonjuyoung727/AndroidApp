package com.example.androidapplication.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

import com.example.androidapplication.model.EventDetail

interface EventApi {
    @GET("/api/events/{eventId}")
    suspend fun getEventDetail(
        @Path("eventId") eventId: Long
    ): Response<EventDetail>
}