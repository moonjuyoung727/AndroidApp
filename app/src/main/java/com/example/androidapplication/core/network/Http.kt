package com.example.androidapplication.core.network

import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.ApiErrorCode
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.FORBIDDEN_MESSAGE
import com.example.androidapplication.core.common.NetworkError
import com.example.androidapplication.core.common.SERVICE_UNAVAILABLE_MESSAGE
import com.example.androidapplication.core.common.SESSION_EXPIRED_MESSAGE
import com.example.androidapplication.core.common.SessionExpiredError
import com.example.androidapplication.core.common.TOO_MANY_REQUESTS_MESSAGE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

interface TokenProvider {
    val currentToken: String?

    /** [token]으로 보낸 요청이 401을 받았다. 같은 토큰이면 세션을 지우고 안내를 한 번만 남긴다. */
    fun onUnauthorized(token: String, serverMessage: String?)
}

/** VPN 내부 서버에 닿았는지를 전역 VPN 상태로 알린다. */
interface ConnectivityReporter {
    fun onVpnServerUnreachable()
    fun onVpnServerReachable()
}

/** 서버 오류 본문 { error: { code, message, details } }를 읽는다. error로 감싸지 않은 본문도 받는다. */
fun parseErrorBody(raw: String?): ErrorBody = runCatching {
    val root = AppJson.parseToJsonElement(raw.orEmpty()).jsonObject
    AppJson.decodeFromJsonElement(ErrorBody.serializer(), root["error"] as? JsonObject ?: root)
}.getOrDefault(ErrorBody())

/** 토큰은 우리 API 호스트로만 보낸다. */
class AuthInterceptor(
    private val tokens: TokenProvider,
    private val apiHosts: Set<String>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.currentToken?.takeIf { request.url.host in apiHosts }
            ?: return chain.proceed(request)

        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build()
        )
        if (response.code == 401) {
            val message = parseErrorBody(response.peekBody(64 * 1024L).string()).message
            tokens.onUnauthorized(token, message)
        }
        return response
    }
}

class ConnectivityInterceptor(
    private val reporter: ConnectivityReporter,
    private val vpnHost: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val isVpnRequest = chain.request().url.host == vpnHost
        try {
            val response = chain.proceed(chain.request())
            if (isVpnRequest) reporter.onVpnServerReachable()
            return response
        } catch (e: IOException) {
            // 요청을 직접 취소한 경우는 네트워크 오류가 아니다
            if (isVpnRequest && !chain.call().isCanceled()) reporter.onVpnServerUnreachable()
            throw e
        }
    }
}

/** Retrofit 호출의 예외를 [AppError]로 바꾼다. */
@Singleton
class ApiCaller @Inject constructor(private val reporter: ConnectivityReporter) {

    suspend fun <T> call(
        fallback: String,
        public: Boolean = false,
        timeoutMs: Long? = null,
        statusFallback: Map<Int, String> = emptyMap(),
        block: suspend () -> T
    ): T = try {
        if (timeoutMs != null) withTimeout(timeoutMs) { block() } else block()
    } catch (e: TimeoutCancellationException) {
        if (!public) reporter.onVpnServerUnreachable()
        throw NetworkError(viaVpn = !public)
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw e.toAppError(statusFallback, fallback)
    } catch (e: IOException) {
        throw NetworkError(viaVpn = !public)
    } catch (e: SerializationException) {
        throw ApiError(0, null, fallback)
    } catch (e: IllegalArgumentException) {
        throw ApiError(0, null, fallback)
    }

    private fun HttpException.toAppError(statusFallback: Map<Int, String>, fallback: String): AppError {
        val raw = response()
        val body = parseErrorBody(raw?.errorBody()?.string())
        val hadToken = raw?.raw()?.request?.header("Authorization") != null
        val kind = ApiErrorCode.resolve(body.errorCode, code())
        if (kind == ApiErrorCode.UNAUTHORIZED && hadToken) return SessionExpiredError(body.message ?: SESSION_EXPIRED_MESSAGE)
        // 서버 message > 화면별 문구 > 코드 공통 문구 > 기본 문구
        val message = body.message?.takeIf { it.isNotBlank() }
            ?: statusFallback[kind.status] ?: statusFallback[code()]
            ?: defaultMessage(kind) ?: fallback
        return ApiError(code(), body.errorCode, message, body.details?.toString())
    }

    /** 서버 message가 없을 때, 화면 문구보다 조치 안내가 중요한 코드만 공통 문구를 쓴다. */
    private fun defaultMessage(kind: ApiErrorCode): String? = when (kind) {
        ApiErrorCode.FORBIDDEN -> FORBIDDEN_MESSAGE
        ApiErrorCode.TOO_MANY_REQUESTS -> TOO_MANY_REQUESTS_MESSAGE
        ApiErrorCode.SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE_MESSAGE
        else -> null
    }
}
