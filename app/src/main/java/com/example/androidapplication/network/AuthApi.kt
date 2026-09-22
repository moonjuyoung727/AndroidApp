package com.example.androidapplication.network

import com.example.androidapplication.model.IdCheckResponse
import com.example.androidapplication.model.SignupRequest
import com.example.androidapplication.model.SignupResponse
import com.example.androidapplication.model.LoginResponse
import com.example.androidapplication.model.LoginRequest

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query


interface AuthApi {
    @POST("/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
    // 백엔드 API 정해지면 추가

    @POST("/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): Response<SignupResponse>

    @GET("/check-id")
    suspend fun checkId(
    @Query("id") id: String
    ): Response<IdCheckResponse>
}