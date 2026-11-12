package com.example.androidapplication.feature.multiview

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.PrivacyZone
import com.example.androidapplication.core.data.PrivacyZoneRepository
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.vpn.VpnMonitor
import com.example.androidapplication.core.webrtc.StreamQuality
import com.example.androidapplication.core.webrtc.StreamUrlResolver
import com.example.androidapplication.core.webrtc.WebRtcEngine
import com.example.androidapplication.core.webrtc.WhepSessionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MultiviewViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val bus: UiMessageBus,
    private val resolver: StreamUrlResolver,
    private val zoneRepository: PrivacyZoneRepository,
    val sessions: WhepSessionFactory,
    vpn: VpnMonitor,
    engine: WebRtcEngine,
    settings: SettingsRepository
) : ViewModel() {
    val cameras = devices.cameras
    val general = settings.general
    val eglContext = engine.eglContext
    val vpnUsable = vpn.usable

    fun subStreamUrl(camera: Camera): String? = resolver.resolve(camera, StreamQuality.Sub)
    fun zones(mac: String): Flow<List<PrivacyZone>> = zoneRepository.observe(mac)

    suspend fun poll(first: Boolean): Boolean = try {
        devices.refresh(silent = !first)
    } catch (e: AppError) {
        bus.showError(e, "카메라 목록 조회에 실패했습니다.")
        false
    }
}

/** 가로는 웹과 같이 2×1, 2×2, 3×2. 세로는 1×2, 2×2, 2×3. (열, 행) */
fun channelLayout(channels: Int, landscape: Boolean): Pair<Int, Int> = when (channels) {
    2 -> if (landscape) 2 to 1 else 1 to 2
    4 -> 2 to 2
    else -> if (landscape) 3 to 2 else 2 to 3
}

/** 이름·위치·MAC 검색. MAC은 콜론 유무와 상관없이 찾는다. */
fun matchesCamera(camera: Camera, keyword: String, status: String): Boolean {
    val query = keyword.trim().lowercase()
    val mac = Mac.normalize(query)
    val matches = query.isEmpty() || camera.name.lowercase().contains(query) ||
        camera.location.lowercase().contains(query) ||
        (mac.isNotEmpty() && camera.mac.contains(mac))
    return matches && (status == "all" || camera.status == status)
}
