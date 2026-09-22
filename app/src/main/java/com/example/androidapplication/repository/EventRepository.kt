package com.example.androidapplication.repository

import com.example.androidapplication.network.EventApi
import com.example.androidapplication.model.EventDetail
import retrofit2.Response

class EventRepository(
    private val api: EventApi
) {
    suspend fun getEventDetail(
        eventId: Long
    ): Response<EventDetail> {
        return api.getEventDetail(
            eventId
        )
    }
}