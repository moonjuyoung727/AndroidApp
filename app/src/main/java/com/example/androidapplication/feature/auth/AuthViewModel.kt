package com.example.androidapplication.feature.auth

import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.ApiErrorCode
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.data.AuthRepository
import com.example.androidapplication.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// title: 버튼·화면 전환 링크 문구, heading: 화면 상단 제목
enum class AuthMode(val title: String, val submit: String, val heading: String = title) {
    Login("로그인", "LOGIN", heading = "Login"),
    Signup("회원가입", "SIGN UP"),
    FindId("이메일 찾기", "FIND EMAIL"),
    FindPassword("비밀번호 찾기", "RESET PASSWORD")
}

data class AuthUi(
    val busy: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
    val info: String? = null,
    val completedMode: AuthMode? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val sessions: SessionManager,
    private val bus: UiMessageBus
) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUi())
    val ui = _ui.asStateFlow()
    val loginNotice = sessions.loginNotice
    val canFindId get() = repository.canFindId
    val canResetPassword get() = repository.canResetPassword

    private val _remembered = MutableStateFlow<Pair<String, Boolean>?>(null)
    val remembered = _remembered.asStateFlow()

    init {
        viewModelScope.launch { _remembered.value = sessions.rememberedLogin() }
    }

    fun clearFeedback() = _ui.update { AuthUi() }

    fun clearCompleted() = _ui.update { it.copy(completedMode = null) }

    private fun invalid(errors: Map<String, String?>): Boolean {
        val found = errors.filterValues { it != null }.mapValues { it.value!! }
        if (found.isNotEmpty()) _ui.update { it.copy(fieldErrors = found, generalError = null, info = null) }
        return found.isNotEmpty()
    }

    /** [badRequestField]가 null이면 400을 특정 칸이 아닌 전체 안내로 보여 준다. */
    private fun submit(badRequestField: String? = FIELD_PASSWORD, block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.value = AuthUi(busy = true)
            try {
                block()
                _ui.update { it.copy(busy = false) }
            } catch (e: ApiError) {
                _ui.value = when (e.kind) {
                    ApiErrorCode.BAD_REQUEST if badRequestField != null -> AuthUi(fieldErrors = mapOf(badRequestField to e.message))
                    ApiErrorCode.CONFLICT -> AuthUi(fieldErrors = mapOf(FIELD_EMAIL to e.message))
                    else -> AuthUi(generalError = e.message)
                }
            } catch (e: AppError) {
                _ui.value = AuthUi(generalError = e.message)
            }
        }
    }

    fun login(email: String, password: String, remember: Boolean) {
        sessions.consumeLoginNotice()
        if (invalid(mapOf(FIELD_EMAIL to Validators.email(email), FIELD_PASSWORD to if (password.isEmpty()) "비밀번호를 입력해 주세요." else null))) return
        submit { sessions.login(repository.login(email.trim(), password, remember), remember) }
    }

    fun signup(name: String, email: String, password: String, confirm: String) {
        if (invalid(
                mapOf(
                    FIELD_NAME to Validators.name(name),
                    FIELD_EMAIL to Validators.email(email),
                    FIELD_PASSWORD to Validators.password(password),
                    FIELD_CONFIRM to Validators.passwordConfirm(password, confirm)
                )
            )
        ) return
        // 400은 이메일·비밀번호·이름 중 어느 칸 문제인지 알 수 없어 서버 안내를 전체 오류로 보여 준다
        submit(badRequestField = null) {
            repository.register(email.trim(), password, name.trim())
            _ui.value = AuthUi(info = "회원가입이 완료되었습니다. 로그인해 주세요.", completedMode = AuthMode.Signup)
        }
    }

    fun findId(name: String, phone: String, code: String) {
        if (invalid(
                mapOf(
                    FIELD_NAME to Validators.name(name),
                    FIELD_PHONE to Validators.phone(phone),
                    FIELD_CODE to Validators.verificationCode(code)
                )
            )
        ) return
        submit {
            val email = repository.findId(name.trim(), phone, code.trim())
            _ui.value = AuthUi(info = "가입된 이메일: $email")
        }
    }

    fun resetPassword(email: String, code: String, password: String, confirm: String) {
        if (invalid(
                mapOf(
                    FIELD_EMAIL to Validators.email(email),
                    FIELD_CODE to Validators.verificationCode(code),
                    FIELD_PASSWORD to Validators.password(password),
                    FIELD_CONFIRM to Validators.passwordConfirm(password, confirm)
                )
            )
        ) return
        viewModelScope.launch {
            _ui.value = AuthUi(busy = true)
            try {
                val message = repository.resetPassword(email.trim(), code.trim(), password)
                _ui.value = AuthUi(info = message ?: "비밀번호가 변경되었습니다. 로그인해 주세요.", completedMode = AuthMode.FindPassword)
            } catch (e: ApiError) {
                // 2.1.5: 400은 인증 코드, 404는 이메일 문제다
                _ui.value = when (e.kind) {
                    ApiErrorCode.BAD_REQUEST -> AuthUi(fieldErrors = mapOf(FIELD_CODE to e.message))
                    ApiErrorCode.NOT_FOUND -> AuthUi(fieldErrors = mapOf(FIELD_EMAIL to e.message))
                    else -> AuthUi(generalError = e.message)
                }
            } catch (e: AppError) {
                _ui.value = AuthUi(generalError = e.message)
            }
        }
    }

    companion object {
        const val FIELD_NAME = "name"
        const val FIELD_EMAIL = "email"
        const val FIELD_PASSWORD = "password"
        const val FIELD_CONFIRM = "confirm"
        const val FIELD_PHONE = "phone"
        const val FIELD_CODE = "code"
    }
}
