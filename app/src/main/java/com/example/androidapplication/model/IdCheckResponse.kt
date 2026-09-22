package com.example.androidapplication.model

import android.os.Message

data class IdCheckResponse(
    val available: Boolean,
    val message: String
)