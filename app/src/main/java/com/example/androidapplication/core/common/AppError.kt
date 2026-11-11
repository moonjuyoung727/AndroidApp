package com.example.androidapplication.core.common

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

const val VPN_CHECK_MESSAGE = "VPN 연결을 확인하세요."
const val SESSION_EXPIRED_MESSAGE = "로그인이 만료되었습니다. 다시 로그인해 주세요."
const val PUBLIC_NETWORK_MESSAGE = "서버에 연결할 수 없습니다. 인터넷 연결을 확인하세요."
const val FEATURE_DISABLED_MESSAGE = "서버 연동 후 사용할 수 있습니다."

open class AppError(override val message: String) : Exception(message)

/** 서버가 오류로 응답했다. 본문은 { error: { code, message, details } }. */
class ApiError(val status: Int, val code: String?, message: String, val details: String? = null) : AppError(message)

/** 서버에 닿지 못했다 (연결 실패·타임아웃). [viaVpn]이면 VPN 배너가 안내를 맡는다. */
class NetworkError(val viaVpn: Boolean) : AppError(if (viaVpn) VPN_CHECK_MESSAGE else PUBLIC_NETWORK_MESSAGE)

/** 토큰이 있는 상태의 401. 세션 만료 처리와 안내는 이미 끝났다. */
class SessionExpiredError(message: String) : AppError(message)

/** 서버 연동 플래그가 꺼져 있다. */
class FeatureDisabledError(message: String = FEATURE_DISABLED_MESSAGE) : AppError(message)

data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/** 화면 어디서든 Snackbar를 띄운다. 수신자가 없는 동안(백그라운드)의 메시지는 복귀 후 보여 준다. */
@Singleton
class UiMessageBus @Inject constructor() {
    private val channel = Channel<UiMessage>(capacity = 16, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        channel.trySend(UiMessage(text, actionLabel, onAction))
    }

    /** 서버 message를 우선 보여 준다. VPN 끊김과 세션 만료는 전용 UI가 맡으므로 띄우지 않는다. */
    fun showError(error: Throwable, fallback: String) {
        when {
            error is SessionExpiredError -> Unit
            error is NetworkError && error.viaVpn -> Unit
            error is AppError -> show(error.message)
            else -> show(fallback)
        }
    }
}
