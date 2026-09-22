package com.example.androidapplication.viewmodel

import androidx.compose.runtime.mutableStateOf
import com.example.androidapplication.repository.EventRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

import com.example.androidapplication.model.EventDetail

class EventDetailViewModel(
    private val repository: EventRepository
) : ViewModel() {

    var eventDetail by mutableStateOf<EventDetail?>(null)
        private set

    fun loadEvent(
        eventId: Long
    ) {
        viewModelScope.launch {
            val response =
                repository.getEventDetail(
                    eventId
                )
            if (response.isSuccessful) {
                eventDetail = response.body()
            }
        }
    }
}