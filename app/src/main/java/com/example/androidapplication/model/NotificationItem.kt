package com.example.androidapplication.model

// 알림 한 건 (서버에서 오는 데이터)
data class NotificationItem(
    val id: Long,
    val title: String,
    val message: String,
    val time: String
)
