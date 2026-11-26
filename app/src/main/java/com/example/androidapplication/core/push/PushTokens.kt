package com.example.androidapplication.core.push

import com.example.androidapplication.BuildConfig
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.config.FeatureFlags
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.PushTokenApi
import com.example.androidapplication.core.network.PushTokenDeleteRequest
import com.example.androidapplication.core.network.PushTokenRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** ICD I4 7.1.6: push_tokens.token은 1~1024자 (DB PK 인덱스 한계) */
const val PUSH_TOKEN_MAX = 1024

/** 이 기기의 푸시 토큰. 없으면 null (토큰을 받기 전이거나 Firebase 연결 전). */
interface PushTokenSource {
    suspend fun current(): String?
}

/**
 * Firebase 연결(C-2) 전의 토큰 공급원. local.properties의 `securecam.debugPushToken` 값을 준다.
 * 비어 있으면(기본값) 토큰이 없는 것으로 보고 서버를 호출하지 않는다. 서버 S-7을 가짜 토큰으로 확인할 때 쓴다.
 */
class DebugPushTokenSource @Inject constructor() : PushTokenSource {
    override suspend fun current(): String? = BuildConfig.DEBUG_PUSH_TOKEN.takeIf { it.isNotBlank() }
}

/**
 * 로그인 세션에 이 기기의 토큰을 등록하고, 로그아웃 직전에 해제한다 (ICD I4 2.7.3).
 * 같은 토큰을 다시 등록하면 서버가 갱신하고, 다른 계정 소유면 현재 계정으로 옮긴다. 그래서 로그인마다 등록한다.
 * 실패는 알리지 않는다. 알림을 못 받아도 이벤트 내역에서 확인할 수 있다 (UC6-E5).
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val source: PushTokenSource,
    private val api: PushTokenApi,
    private val caller: ApiCaller
) {
    private val lock = Mutex()

    /** 현재 세션에 등록된 토큰. 등록 전이거나 세션이 끝나면 null */
    private var registered: String? = null

    val isRegistered: Boolean get() = registered != null

    /** 세션이 LoggedIn이 될 때, 토큰이 바뀔 때(onNewToken) 부른다. 성공하면 true */
    suspend fun register(token: String? = null): Boolean = lock.withLock {
        if (!FeatureFlags.pushToken) return false
        val value = (token ?: source.current())?.takeIf { it.length in 1..PUSH_TOKEN_MAX } ?: return false
        try {
            caller.call("푸시 토큰 등록 실패") { api.register(PushTokenRequest(value)) }
            registered = value
            true
        } catch (_: AppError) {
            false
        }
    }

    /** 로그아웃 직전에 부른다. 서버 로그아웃 뒤에는 인증을 통과하지 못하므로 먼저 불러야 한다 */
    suspend fun unregister() = lock.withLock {
        val value = registered ?: return@withLock
        registered = null
        if (!FeatureFlags.pushToken) return@withLock
        try {
            caller.call("푸시 토큰 해제 실패") { api.unregister(PushTokenDeleteRequest(value)) }
        } catch (_: AppError) {
            // 해제가 실패해도 로그아웃은 계속한다
        }
    }

    /** 세션이 서버 호출 없이 끝났을 때(토큰 만료 등) 기록만 지운다. 서버에는 토큰이 남는다 [TBR] */
    fun forget() {
        registered = null
    }
}
