// 로그인 요청 시 보낼 데이터
package com.example.androidapplication.model

data class LoginRequest(
    val id: String,
    val password: String
)