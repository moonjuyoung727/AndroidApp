// 로그인 후 서버가 보내는 응답 데이터
package com.example.androidapplication.model

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val token: String
)