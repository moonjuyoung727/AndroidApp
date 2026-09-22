package com.example.androidapplication.repository

import com.example.androidapplication.network.AuthApi
import com.example.androidapplication.model.IdCheckResponse
import com.example.androidapplication.model.LoginRequest
import com.example.androidapplication.model.LoginResponse
import com.example.androidapplication.model.SignupRequest
import com.example.androidapplication.model.SignupResponse
import retrofit2.Response

class AuthRepository(
    private val api: AuthApi
) {

    //로그인
    suspend fun login(
        id: String,
        password: String
    ): Response<LoginResponse> {

        val request = LoginRequest(
            id = id,
            password = password
        )

        return api.login(request)
    }

    //회원가입
    suspend fun signup(
        id: String,
        password: String,
        email: String
    ) : Response<SignupResponse> {

        val request = SignupRequest(
            id = id,
            password = password,
            email = email
        )

        return api.signup(request)
    }

    suspend fun checkId(
        id: String
    ): Response<IdCheckResponse> {
        return api.checkId(id)
    }
}