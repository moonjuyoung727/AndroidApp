package com.example.androidapplication.viewmodel

import com.example.androidapplication.repository.AuthRepository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.launch
import java.io.IOException


class LoginViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    var loginMessage by mutableStateOf("")
        private set

    var isLoginError by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var loginSuccess by mutableStateOf(false)
        private set

    fun login(
        id: String,
        password: String
    ) {
        // 서버 연결 전 개발용: 서버 요청 없이 바로 로그인 성공 처리
        if (SKIP_SERVER_LOGIN) {
            loginSuccess = true
            return
        }

        viewModelScope.launch {
            isLoading = true
            loginMessage = ""
            isLoginError = false

            try {
                val response = repository.login(
                    id = id,
                    password = password
                )

                when {
                    //HTTP 200~299
                    response.isSuccessful -> {
                        val body = response.body()

                        if (body == null) {
                            loginMessage = "서버 응답이 올바르지 않습니다."
                            isLoginError = true
                        } else if (body.success) {
                            loginSuccess = true
                            loginMessage = ""
                            println(
                                "token: ${body.token}"
                            )
                        } else {
                            loginMessage =
                                body.message
                            isLoginError = true
                        }
                    }

                    //로그인 정보 틀림
                    response.code() == 401 -> {
                        loginMessage =
                            "아이디 또는 비밀번호가 올바르지 않습니다. 입력한 정보를 다시 확인해 주세요."
                        isLoginError = true
                    }

                    // 기타 HTTP 오류
                    else -> {
                        loginMessage =
                            "로그인 요청에 실패했습니다. (${response.code()})"
                        isLoginError = true
                    }
                }
            } catch (e: IOException) {

                loginMessage = "서버에 연결할 수 없습니다."
                isLoginError = true
            } catch (e: Exception) {
                loginMessage =
                    "로그인 처리 중 오류가 발생했습니다."
                isLoginError = true
            } finally {
                isLoading = false
            }
        }
    }

    fun consumeLoginSuccess() {
        loginSuccess = false
    }

    private companion object {
        // 서버 연결이 끝나면 false 로 바꾸거나 이 분기를 삭제할 것
        const val SKIP_SERVER_LOGIN = true
    }
}