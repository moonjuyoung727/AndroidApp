package com.example.androidapplication.core.config

import com.example.androidapplication.BuildConfig

/** ICD 5.1 VPN 값과 서버 주소. 보드 대역(10.0.1.x)으로 직접 요청하지 않고 항상 서버를 거친다. */
object ApiConfig {
    val publicBaseUrl: String = BuildConfig.API_BASE_URL_PUBLIC.trimEnd('/') + "/"
    val vpnBaseUrl: String = BuildConfig.API_BASE_URL_VPN.trimEnd('/') + "/"

    const val VPN_SERVER_IP = "10.0.0.100"
    const val VPN_PORT = 51820
    const val VPN_BOARD_SUBNET = "10.0.1.0/24"
    const val VPN_CLIENT_SUBNET = "10.0.2.0/24"

    fun isInSubnet(ip: String?, subnet: String): Boolean {
        val prefix = subnet.substringBefore('/').split('.').take(3).joinToString(".")
        return ip != null && ip.startsWith("$prefix.")
    }
}

/**
 * 서버 호출 스위치. 값만 바꾸면 Remote 구현이 연결된다.
 * 꺼진 기능은 저장되는 것처럼 보이지 않도록 화면에서 비활성으로 표시한다.
 */
object FeatureFlags {
    // 서버 구현됨
    val eventList = true
    val vpnConnections = true

    // 명세만 있고 서버 확인 전
    val eventUpdate = false
    val deviceStatus = false
    val deviceUpdate = false
    val privacyZones = false
    val cloudStorage = false
    val subscription = false
    val paymentMethods = false // Google Play 결제 정책 검토 전까지 끔
    val accountDeletion = false

    // 요구사항 APP-F-051/052/061과 ICD 2.1에 정의된 API
    val findId = true
    val resetPassword = true
    val loginSessions = true

    // API 명세 없음 (사용자 정보·비밀번호 변경)
    val profileUpdate = false
    val passwordChange = false

    // APP-F-054 로그인 후 자동 연결. I2 범위 밖이라 끈다 (폰은 WireGuard 공식 앱으로 연결)
    val embeddedVpnTunnel = false
}

/** 프라이버시 존 화면 제한 (ICD는 보드당 20개, 화면은 4개). */
object PrivacyZoneLimits {
    const val MAX_ZONES = 4
    const val MIN_SIZE = 0.03f
    const val NAME_MAX = 20
}
