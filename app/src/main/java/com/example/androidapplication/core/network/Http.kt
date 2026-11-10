package com.example.androidapplication.core.network

import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.NetworkError
import com.example.androidapplication.core.common.SESSION_EXPIRED_MESSAGE
import com.example.androidapplication.core.common.SessionExpiredError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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

fun parseErrorBody(raw: String?): ErrorBody =
    runCatching { AppJson.decodeFromString<ErrorBody>(raw.orEmpty()) }.getOrDefault(ErrorBody())

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
        throw e.toAppError(statusFallback[e.code()] ?: fallback)
    } catch (e: IOException) {
        throw NetworkError(viaVpn = !public)
    } catch (e: SerializationException) {
        throw ApiError(0, null, fallback)
    } catch (e: IllegalArgumentException) {
        throw ApiError(0, null, fallback)
    }

    private fun HttpException.toAppError(fallback: String): AppError {
        val raw = response()
        val body = parseErrorBody(raw?.errorBody()?.string())
        val hadToken = raw?.raw()?.request?.header("Authorization") != null
        if (code() == 401 && hadToken) return SessionExpiredError(body.message ?: SESSION_EXPIRED_MESSAGE)
        return ApiError(code(), body.errorCode, body.message?.takeIf { it.isNotBlank() } ?: fallback, body.details?.toString())
    }
}
