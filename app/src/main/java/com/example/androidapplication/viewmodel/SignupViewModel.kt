package com.example.androidapplication.viewmodel

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.androidapplication.repository.AuthRepository

import kotlinx.coroutines.launch
import java.io.IOException


class SignupViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    //아이디 중복 확인 상태
    var isCheckingId by mutableStateOf(false)
        private set
    var isIdChecked by mutableStateOf(false)
        private set
    var isIdAvailable by mutableStateOf(false)
        private set
    var idCheckMessage by mutableStateOf("")
        private set

    //회원가입 상태
    var isSigningUp by mutableStateOf(false)
        private set
    var signupMessage by mutableStateOf("")
        private set
    var signupSuccess by mutableStateOf(false)
        private set

    //아이디 수정 시 이전 중복확인 결과 삭제
    fun resetIdCheck() {
        isIdChecked = false
        isIdAvailable = false
        idCheckMessage = ""
    }

    //아이디 중복 확인
    fun checkId(
        id: String
    ) {
        viewModelScope.launch {
            isCheckingId = true

            try {
                val response =
                    repository.checkId(id)

                when {
                    response.isSuccessful -> {
                        val body = response.body()

                        if (body == null) {
                            isIdChecked = false
                            isIdAvailable = false

                            idCheckMessage = "서버 응답이 올바르지 않습니다."
                        } else {
                            //중복 확인 요청 자체 완료
                            isIdChecked = true

                            //사용 가능 여부
                            isIdAvailable = body.available
                            idCheckMessage = body.message
                        }
                    }

                    else -> {
                        isIdChecked = false
                        isIdAvailable = false

                        idCheckMessage = "아이디 중복 확인에 실패했습니다. (${response.code()})"
                    }
                }
            } catch (e: IOException) {
                isIdChecked = false
                isIdAvailable = false

                idCheckMessage = "서버에 연결할 수 없습니다."
            } catch (e: Exception) {
                isIdChecked = false
                isIdAvailable = false

                idCheckMessage = "아이디 중복 확인 중 오류가 발생했습니다."
            } finally {
                isCheckingId = false
            }
        }
    }

    //회원가입
    fun signup(
        id: String,
        password: String,
        email: String
    ) {
        viewModelScope.launch {
            signupMessage = ""
            signupSuccess = false

            //중복 확인 여부
            if (!isIdChecked) {
                signupMessage =
                    "아이디 중복 확인을 해주세요."
                return@launch
            }

            //사용 불가능한 아이디
            if (!isIdAvailable) {
                signupMessage = "사용할 수 없는 아이디입니다."
                return@launch
            }

            isSigningUp = true

            try {
                val response =
                    repository.signup(
                        id = id,
                        password = password,
                        email = email
                    )
                when {

                    //HTTP 2xx
                    response.isSuccessful -> {
                        val body = response.body()

                        if (body == null) {
                            signupMessage = "서버 응답이 올바르지 않습니다."
                        } else if (body.success) {
                            signupSuccess = true
                            signupMessage = body.message
                        } else {
                            signupMessage = body.message
                        }
                    }

                    //중복 데이터 -> 409 쓴다면
                    response.code() == 409 -> {
                        signupMessage = "이미 사용 중인 아이디 또는 이메일입니다."
                    }

                    //입력값 문제
                    response.code() == 400 -> {

                        signupMessage = "입력한 정보를 다시 확인해 주세요."
                    }

                    //기타 오류
                    else -> {
                        signupMessage = "회원가입에 실패했습니다. (${response.code()})"
                    }
                }
            } catch (e: IOException) {
                signupMessage = "서버에 연결할 수 없습니다."
            } catch (e: Exception) {
                signupMessage = "회원가입 처리 중 오류가 발생했습니다."
            } finally {
                isSigningUp = false
            }
        }
    }

    fun consumeSignupSuccess() {
        signupSuccess = false
    }
}
