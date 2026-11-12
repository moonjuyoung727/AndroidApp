package com.example.androidapplication

import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.ApiErrorCode
import com.example.androidapplication.core.common.LOGIN_INVALID_CREDENTIALS_MESSAGE
import com.example.androidapplication.core.common.LOGIN_TOO_MANY_REQUESTS_MESSAGE
import com.example.androidapplication.core.common.SIGNUP_EMAIL_EXISTS_MESSAGE
import com.example.androidapplication.core.data.forLogin
import com.example.androidapplication.core.data.forSignup
import com.example.androidapplication.core.common.NetworkError
import com.example.androidapplication.core.common.SERVICE_UNAVAILABLE_MESSAGE
import com.example.androidapplication.core.common.SESSION_EXPIRED_MESSAGE
import com.example.androidapplication.core.common.TOO_MANY_REQUESTS_MESSAGE
import com.example.androidapplication.core.common.SessionExpiredError
import com.example.androidapplication.core.common.UiMessage
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.network.AuthInterceptor
import com.example.androidapplication.core.network.ConnectivityInterceptor
import com.example.androidapplication.core.network.ConnectivityReporter
import com.example.androidapplication.core.network.GeneralSettingsDto
import com.example.androidapplication.core.network.TokenProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

/** 완료 기준: 401·네트워크 오류·서버 오류가 각각 정해진 처리로 이어진다. */
class HttpErrorHandlingTest {
    private interface TestApi {
        @GET("general-settings") suspend fun get(): GeneralSettingsDto
    }

    private class FakeTokens(override var currentToken: String? = "token") : TokenProvider {
        val unauthorized = mutableListOf<Pair<String, String?>>()
        override fun onUnauthorized(token: String, serverMessage: String?) {
            unauthorized += token to serverMessage
            currentToken = null
        }
    }

    private class FakeReporter : ConnectivityReporter {
        var unreachable = 0
        var reachable = 0
        override fun onVpnServerUnreachable() { unreachable++ }
        override fun onVpnServerReachable() { reachable++ }
    }

    private val server = MockWebServer()
    private val tokens = FakeTokens()
    private val reporter = FakeReporter()
    private lateinit var api: TestApi
    private val caller = ApiCaller(reporter)

    @Before fun setUp() {
        server.start()
        val host = server.hostName
        val client = OkHttpClient.Builder()
            .addInterceptor(ConnectivityInterceptor(reporter, host))
            .addInterceptor(AuthInterceptor(tokens, setOf(host)))
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TestApi::class.java)
    }

    @After fun tearDown() = server.close()

    private fun enqueue(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private suspend inline fun <reified T : Throwable> failure(): T {
        try {
            caller.call("기본 문구") { api.get() }
        } catch (e: Throwable) {
            assertTrue("unexpected: $e", e is T)
            return e as T
        }
        throw AssertionError("예외가 없습니다")
    }

    @Test fun bearer_token_is_attached() = runTest {
        enqueue(200, "{}")
        caller.call("x") { api.get() }
        assertEquals("Bearer token", server.takeRequest().headers["Authorization"])
    }

    @Test fun missing_fields_use_defaults() = runTest {
        enqueue(200, """{"default_channel":4}""")
        val dto = caller.call("x") { api.get() }
        assertEquals(4, dto.defaultChannel)
        assertTrue(dto.autoRefresh)
    }

    @Test fun unauthorized_with_token_expires_session_with_server_message() = runTest {
        enqueue(401, """{"error_code":"TOKEN_EXPIRED","message":"세션 끝"}""")
        val error = failure<SessionExpiredError>()
        assertEquals("세션 끝", error.message)
        assertEquals(listOf("token" to "세션 끝"), tokens.unauthorized)
    }

    @Test fun unauthorized_without_message_uses_default() = runTest {
        enqueue(401)
        assertEquals(SESSION_EXPIRED_MESSAGE, failure<SessionExpiredError>().message)
    }

    @Test fun unauthorized_without_token_is_a_normal_error() = runTest {
        tokens.currentToken = null
        enqueue(401, """{"message":"로그인 실패"}""")
        val error = failure<ApiError>()
        assertEquals(401, error.status)
        assertTrue(tokens.unauthorized.isEmpty())
    }

    @Test fun server_error_uses_message_then_fallback() = runTest {
        enqueue(500, """{"error_code":"E","message":"서버 문제","details":{"a":1}}""")
        val error = failure<ApiError>()
        assertEquals("서버 문제", error.message)
        assertEquals("E", error.code)

        enqueue(500, "not json")
        assertEquals("기본 문구", failure<ApiError>().message)
    }

    @Test fun wrapped_error_body_is_read() = runTest {
        enqueue(404, """{"error":{"code":"NOT_FOUND","message":"보드 없음","details":{}}}""")
        val error = failure<ApiError>()
        assertEquals("보드 없음", error.message)
        assertEquals("NOT_FOUND", error.code)
    }

    @Test fun error_code_decides_kind_before_status() {
        assertEquals(ApiErrorCode.CONFLICT, ApiError(400, "CONFLICT", "m").kind)
        assertEquals(ApiErrorCode.NOT_FOUND, ApiError(404, null, "m").kind)
        assertEquals(ApiErrorCode.BAD_REQUEST, ApiError(400, "SOMETHING_NEW", "m").kind)
        assertEquals(ApiErrorCode.UNKNOWN, ApiError(418, null, "m").kind)
    }

    @Test fun unauthorized_code_expires_session() = runTest {
        enqueue(401, """{"error":{"code":"UNAUTHORIZED","message":"토큰 만료"}}""")
        assertEquals("토큰 만료", failure<SessionExpiredError>().message)
    }

    @Test fun code_default_message_when_server_message_missing() = runTest {
        enqueue(503, """{"error":{"code":"SERVICE_UNAVAILABLE"}}""")
        val error = failure<ApiError>()
        assertEquals(ApiErrorCode.SERVICE_UNAVAILABLE, error.kind)
        assertEquals(SERVICE_UNAVAILABLE_MESSAGE, error.message)

        enqueue(429, """{"error":{"code":"TOO_MANY_REQUESTS"}}""")
        assertEquals(TOO_MANY_REQUESTS_MESSAGE, failure<ApiError>().message)

        // 500은 화면별 기본 문구를 그대로 쓴다
        enqueue(500, """{"error":{"code":"INTERNAL_SERVER_ERROR"}}""")
        assertEquals("기본 문구", failure<ApiError>().message)
    }

    @Test fun login_errors_use_fixed_messages() {
        val invalid = ApiError(400, "INVALID_CREDENTIALS", "서버 문구").forLogin()
        assertEquals(LOGIN_INVALID_CREDENTIALS_MESSAGE, invalid.message)
        assertEquals("INVALID_CREDENTIALS", invalid.code)
        assertEquals(LOGIN_TOO_MANY_REQUESTS_MESSAGE, ApiError(429, null, "서버 문구").forLogin().message)
        assertEquals("서버 문구", ApiError(500, null, "서버 문구").forLogin().message)
    }

    @Test fun signup_conflict_uses_fixed_message_but_400_keeps_server_guidance() {
        assertEquals(SIGNUP_EMAIL_EXISTS_MESSAGE, ApiError(409, "CONFLICT", "서버 문구").forSignup().message)
        assertEquals("이메일 형식 오류", ApiError(400, "BAD_REQUEST", "이메일 형식 오류").forSignup().message)
    }

    @Test fun status_specific_fallback() = runTest {
        enqueue(429)
        try {
            caller.call("기본", statusFallback = mapOf(429 to "시도 초과")) { api.get() }
        } catch (e: ApiError) {
            assertEquals("시도 초과", e.message)
        }
    }

    @Test fun unreachable_server_reports_vpn_problem() = runTest {
        server.close()
        val error = failure<NetworkError>()
        assertTrue(error.viaVpn)
        assertEquals("VPN 연결을 확인하세요.", error.message)
        assertEquals(1, reporter.unreachable)
    }

    @Test fun malformed_success_body_does_not_crash() = runTest {
        enqueue(200, "<html>")
        assertEquals("기본 문구", failure<ApiError>().message)
    }

    @Test fun successful_response_reports_reachable() = runTest {
        enqueue(200, "{}")
        caller.call("x") { api.get() }
        assertEquals(1, reporter.reachable)
    }

    @Test fun token_is_not_sent_to_other_hosts() {
        val other = AuthInterceptor(tokens, setOf("api.example.test"))
        val client = OkHttpClient.Builder().addInterceptor(other).build()
        enqueue(200, "{}")
        client.newCall(okhttp3.Request.Builder().url(server.url("/x")).build()).execute().close()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test fun bus_hides_vpn_and_session_errors_but_shows_others() {
        val bus = UiMessageBus()
        bus.showError(NetworkError(viaVpn = true), "f")
        bus.showError(SessionExpiredError("s"), "f")
        bus.showError(NetworkError(viaVpn = false), "f")
        bus.showError(ApiError(500, null, "서버 문제"), "f")
        bus.showError(IllegalStateException(), "기본")
        val received = mutableListOf<UiMessage>()
        runBlocking { repeat(3) { received += bus.messages.first() } }
        assertEquals(3, received.size)
        assertFalse(received.any { it.text == "s" })
        assertEquals("서버 문제", received[1].text)
        assertEquals("기본", received[2].text)
    }
}
