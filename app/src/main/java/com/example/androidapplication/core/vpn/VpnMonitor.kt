package com.example.androidapplication.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.ConnectivityReporter
import com.example.androidapplication.core.network.VpnApi
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class ClientVpnState(val label: String) {
    Connecting("연결 확인 중"),
    Connected("정상"),
    Disconnected("연결 끊김"),
    Error("오류")
}

data class VpnUiState(
    val status: ClientVpnState = ClientVpnState.Disconnected,
    val bannerDismissed: Boolean = false
) {
    val showBanner: Boolean get() = status == ClientVpnState.Error && !bannerDismissed
}

/** 이 앱의 트래픽이 VPN을 타는지 묻는다. 서버 판단(/status)과 분리해 디버그 Fake로 바꿀 수 있다. */
interface ClientVpnProbe {
    suspend fun isConnected(transportActive: Boolean): Boolean
}

class RemoteClientVpnProbe @Inject constructor(
    private val api: Lazy<VpnApi>,
    private val caller: Lazy<ApiCaller>
) : ClientVpnProbe {
    override suspend fun isConnected(transportActive: Boolean): Boolean {
        val status = caller.get().call("VPN 상태 확인 실패") { api.get().clientStatus() }
        return isClientVpnConnected(status.connected, status.clientIp, transportActive)
    }
}

/** client_ip가 클라이언트 대역 밖이면 미연결로 본다. */
fun isClientVpnConnected(serverConnected: Boolean, clientIp: String?, transportActive: Boolean): Boolean {
    if (clientIp != null && !ApiConfig.isInSubnet(clientIp, ApiConfig.VPN_CLIENT_SUBNET)) return false
    return serverConnected && transportActive
}

/** Client VPN 상태를 전역으로 공유한다. */
@Singleton
class VpnMonitor @Inject constructor(
    @param:ApplicationContext context: Context,
    private val probe: Lazy<ClientVpnProbe>
) : ConnectivityReporter {
    private val _ui = MutableStateFlow(VpnUiState())
    val ui: StateFlow<VpnUiState> = _ui.asStateFlow()

    @Volatile
    var transportActive: Boolean = false
        private set

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    init {
        // 앱 기준 기본 네트워크를 본다. 앱이 분할 터널에서 제외되면 VPN이 아닌 네트워크가 온다
        transportActive = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                onTransportChanged(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            }

            override fun onLost(network: Network) = onTransportChanged(false)
        })
    }

    private fun onTransportChanged(active: Boolean) {
        val lost = transportActive && !active
        transportActive = active
        if (lost) _ui.update { if (it.status == ClientVpnState.Connected) it.copy(status = ClientVpnState.Error) else it }
    }

    suspend fun check(): Boolean {
        setStatus(ClientVpnState.Connecting)
        val connected = runCatching { probe.get().isConnected(transportActive) }.getOrDefault(false)
        setStatus(if (connected) ClientVpnState.Connected else ClientVpnState.Error)
        return connected
    }

    fun dismissBanner() = _ui.update { it.copy(bannerDismissed = true) }

    fun reset() {
        _ui.value = VpnUiState()
    }

    override fun onVpnServerUnreachable() = setStatus(ClientVpnState.Error)

    override fun onVpnServerReachable() {
        if (_ui.value.status == ClientVpnState.Error && transportActive) setStatus(ClientVpnState.Connected)
    }

    private fun setStatus(status: ClientVpnState) = _ui.update {
        // 닫아 둔 배너도 회복됐다가 다시 끊기면 다시 보여 준다
        it.copy(status = status, bannerDismissed = it.bannerDismissed && status != ClientVpnState.Connected)
    }
}
