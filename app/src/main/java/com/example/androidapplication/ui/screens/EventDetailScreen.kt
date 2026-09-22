package com.example.androidapplication.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.androidapplication.ui.components.HlsPlayer
import com.example.androidapplication.model.EventDetail
import com.example.androidapplication.viewmodel.EventDetailViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


import com.example.androidapplication.repository.EventRepository

@Composable
fun EventDetailScreen(
    eventId: Long,
    viewModel: EventDetailViewModel
) {
    val event = viewModel.eventDetail

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    if (event == null) {
        Text("이벤트 정보를 불러오는 중 ...")
        return
    }

    Column {
        Text(event.cameraName)
        Text(event.eventType)

        HlsPlayer(
            src = event.videoUrl,
            modifier = Modifier.fillMaxWidth()
        )
    }
}