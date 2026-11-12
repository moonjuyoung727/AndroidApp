package com.example.androidapplication.core.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.androidapplication.core.common.READ_TIMEOUT_MS
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.ConnectivityReporter
import com.example.androidapplication.core.network.VpnApi
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
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

/** 이 앱의 트래픽이 VPN을 타는지 묻는다 (서버 /status 판단). */
interface ClientVpnProbe {
    suspend fun isConnected(transportActive: Boolean): Boolean
}

class RemoteClientVpnProbe @Inject constructor(
    private val api: Lazy<VpnApi>,
    private val caller: Lazy<ApiCaller>
) : ClientVpnProbe {
    override suspend fun isConnected(transportActive: Boolean): Boolean {
        val status = caller.get().call("VPN 상태 확인 실패", timeoutMs = READ_TIMEOUT_MS) { api.get().clientStatus() }
        return isClientVpnConnected(status.connected, status.clientIp, transportActive)
    }
}

/** client_ip가 클라이언트 대역 밖이면 미연결로 본다. */
fun isClientVpnConnected(serverConnected: Boolean, clientIp: String?, transportActive: Boolean): Boolean {
    if (clientIp != null && !ApiConfig.isInSubnet(clientIp, ApiConfig.VPN_CLIENT_SUBNET)) return false
    return serverConnected && transportActive
}

/** VPN 끊김 중 /status 재확인 간격. */
const val VPN_RECHECK_MS = 10_000L

/** [usable]이 false인 동안 [intervalMs]마다 [recheck]를 부른다. 회복되면 멈추고, 다시 끊기면 처음부터 잰다. */
suspend fun recheckWhileOffline(usable: Flow<Boolean>, intervalMs: Long = VPN_RECHECK_MS, recheck: suspend () -> Unit) {
    usable.collectLatest { ok ->
        if (ok) return@collectLatest
        while (true) {
            delay(intervalMs)
            recheck()
        }
    }
}

/** Client VPN 상태를 전역으로 공유한다. */
@Singleton
class VpnMonitor @Inject constructor(
    @param:ApplicationContext context: Context,
    private val probe: Lazy<ClientVpnProbe>,
    @param:Named("app") private val appScope: CoroutineScope
) : ConnectivityReporter {
    private val _ui = MutableStateFlow(VpnUiState())
    val ui: StateFlow<VpnUiState> = _ui.asStateFlow()

    /** 조용한 재확인이 진행 중이다. 겹쳐 부르지 않는다. */
    private val probing = AtomicBoolean(false)

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

    /**
     * VPN 오류가 아니면 true. false인 동안 자동 반복 조회와 영상 재연결은 멈추고,
     * true로 돌아오면 기다리던 조회와 재연결이 바로 다시 실행된다.
     */
    val usable: Flow<Boolean> = _ui.map { it.status != ClientVpnState.Error }.distinctUntilChanged()

    /** VPN 오류인 동안 /status만 10초 간격으로 확인한다. 호출한 쪽이 앱이 보일 때만 돌린다. */
    suspend fun recheckWhileOffline() = recheckWhileOffline(usable) { recheckQuietly() }

    /**
     * '연결 확인 중'으로 바꾸지 않는다. 바꾸면 그 사이 멈춰 둔 조회·재연결이 풀린다.
     * 이미 확인 중이면 그 결과를 따른다.
     */
    private suspend fun recheckQuietly() {
        if (!probing.compareAndSet(false, true)) return
        val connected = try {
            probe.get().isConnected(transportActive)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            probing.set(false)
        }
        // 그 사이 사용자 확인 등으로 상태가 바뀌었으면 그쪽 결과를 따른다
        if (connected) _ui.update {
            if (it.status == ClientVpnState.Error) it.copy(status = ClientVpnState.Connected, bannerDismissed = false) else it
        }
    }

    fun dismissBanner() = _ui.update { it.copy(bannerDismissed = true) }

    fun reset() {
        _ui.value = VpnUiState()
    }

    override fun onVpnServerUnreachable() = setStatus(ClientVpnState.Error)

    /**
     * 응답을 받았다고 연결됨으로 바꾸지 않는다. 판정은 /status 한 곳에서 하므로 그 즉시 한 번 확인한다.
     * CloudFront 등을 거친 응답이어도 /status의 client_ip 검사에서 걸러진다.
     */
    override fun onVpnServerReachable() {
        if (_ui.value.status != ClientVpnState.Error || probing.get()) return
        appScope.launch { recheckQuietly() }
    }

    private fun setStatus(status: ClientVpnState) = _ui.update {
        // 닫아 둔 배너도 회복됐다가 다시 끊기면 다시 보여 준다
        it.copy(status = status, bannerDismissed = it.bannerDismissed && status != ClientVpnState.Connected)
    }
}
