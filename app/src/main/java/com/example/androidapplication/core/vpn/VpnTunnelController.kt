package com.example.androidapplication.core.vpn

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.config.FeatureFlags
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.network.PublicApi
import com.example.androidapplication.core.network.VpnClientRegisterRequest
import com.example.androidapplication.core.network.VpnClientRegisterResponse
import com.example.androidapplication.core.session.TokenCipher
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed interface TunnelResult {
    data object Up : TunnelResult
    /** 시스템 VPN 동의 화면을 띄워야 한다. 동의 후 다시 호출한다. */
    data class NeedsConsent(val intent: Intent) : TunnelResult
    /** 앱이 직접 연결할 수 없다. 공식 WireGuard 앱 안내로 넘어간다. */
    data class Unavailable(val reason: String?) : TunnelResult
}

/** 터널을 켜고 끄는 방법을 숨긴다. 연결 여부 판단은 [VpnMonitor]가 맡는다. */
interface VpnTunnelController {
    suspend fun ensureUp(): TunnelResult
    suspend fun down()
}

/** 앱이 터널을 직접 켜지 않는 구현 (디버그 Fake 또는 플래그 꺼짐). */
class NoOpTunnelController @Inject constructor() : VpnTunnelController {
    override suspend fun ensureUp(): TunnelResult = TunnelResult.Unavailable(null)
    override suspend fun down() = Unit
}

/**
 * APP-F-054: 로그인 후 앱이 Client VPN을 직접 연결한다.
 * 개인키는 기기에서 만들어 Keystore로 암호화해 두고, 공개키만 서버(2.8.1, 공인 HTTPS)에 등록한다.
 */
@Singleton
class EmbeddedWireGuardController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: PublicApi,
    private val caller: ApiCaller,
    @param:Named("settings") private val store: DataStore<Preferences>,
    private val cipher: TokenCipher
) : VpnTunnelController {
    private val backend by lazy { GoBackend(context) }
    private val tunnel = object : Tunnel {
        override fun getName() = "securecam"
        override fun onStateChange(newState: Tunnel.State) = Unit
    }

    override suspend fun ensureUp(): TunnelResult = withContext(Dispatchers.IO) {
        if (!FeatureFlags.embeddedVpnTunnel) return@withContext TunnelResult.Unavailable(null)
        try {
            // 서버에 등록 API가 없으면 시스템 VPN 동의 화면을 띄우지 않고 공식 앱 안내로 넘어간다
            val config = loadOrRegister()
                ?: return@withContext TunnelResult.Unavailable("VPN 설정 정보를 받지 못했습니다.")
            VpnService.prepare(context)?.let { return@withContext TunnelResult.NeedsConsent(it) }
            backend.setState(tunnel, Tunnel.State.UP, config)
            TunnelResult.Up
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TunnelResult.Unavailable(e.message)
        }
    }

    override suspend fun down() {
        withContext(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        }
    }

    private suspend fun loadOrRegister(): Config? {
        val prefs = store.data.first()
        val privateKey = prefs[KEY_PRIVATE]?.let(cipher::decrypt)?.let(Key::fromBase64)
        val saved = prefs[KEY_PEER]?.let(cipher::decrypt)
            ?.let { runCatching { AppJson.decodeFromString<SavedPeer>(it) }.getOrNull() }
        if (privateKey != null && saved != null) return buildConfig(privateKey, saved)

        val keys = KeyPair()
        val response = try {
            caller.call("클라이언트 VPN 등록 실패", public = true) {
                api.registerVpnClient(VpnClientRegisterRequest(keys.publicKey.toBase64(), clientName()))
            }
        } catch (e: ApiError) {
            if (e.status == 404) return null // 서버에 등록 API가 아직 없다
            throw e
        }
        val peer = response.toSavedPeer() ?: return null
        store.edit {
            it[KEY_PRIVATE] = cipher.encrypt(keys.privateKey.toBase64())
            it[KEY_PEER] = cipher.encrypt(AppJson.encodeToString(SavedPeer.serializer(), peer))
        }
        return buildConfig(keys.privateKey, peer)
    }

    // 앱 트래픽만 터널로 보낸다
    private fun buildConfig(privateKey: Key, peer: SavedPeer): Config = Config.Builder()
        .setInterface(
            Interface.Builder()
                .addAddress(InetNetwork.parse("${peer.vpnIp}/32"))
                .setKeyPair(KeyPair(privateKey))
                .includeApplication(context.packageName)
                .build()
        )
        .addPeer(
            Peer.Builder()
                .setPublicKey(Key.fromBase64(peer.serverPubkey))
                .setEndpoint(InetEndpoint.parse(peer.endpoint))
                .addAllowedIps(peer.allowedIps.map(InetNetwork::parse))
                .setPersistentKeepalive(25)
                .build()
        )
        .build()

    private fun clientName() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun VpnClientRegisterResponse.toSavedPeer(): SavedPeer? {
        if (vpnIp.isBlank() || serverPubkey.isBlank() || serverEndpoint.isBlank()) return null
        val ips = allowedIps.toCidrList().ifEmpty { listOf("10.0.0.1/32") }
        return SavedPeer(vpnIp.substringBefore('/'), serverPubkey, serverEndpoint, ips)
    }

    private fun JsonElement?.toCidrList(): List<String> = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        is JsonPrimitive -> contentOrNull.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }

    @Serializable
    private data class SavedPeer(
        val vpnIp: String,
        val serverPubkey: String,
        val endpoint: String,
        val allowedIps: List<String>
    )

    private companion object {
        val KEY_PRIVATE = stringPreferencesKey("wg_private_enc")
        val KEY_PEER = stringPreferencesKey("wg_peer_enc")
    }
}

sealed interface ConnectOutcome {
    data object Connected : ConnectOutcome
    data class NeedsConsent(val intent: Intent) : ConnectOutcome
    data object Failed : ConnectOutcome
}

/** 터널이 없으면 앱이 직접 켜 보고(APP-F-054), 결과를 전역 VPN 상태에 반영한다. */
@Singleton
class ClientVpnConnector @Inject constructor(
    private val tunnel: VpnTunnelController,
    private val monitor: VpnMonitor
) {
    suspend fun connectAndCheck(): ConnectOutcome {
        if (!monitor.transportActive) {
            when (val result = tunnel.ensureUp()) {
                is TunnelResult.NeedsConsent -> return ConnectOutcome.NeedsConsent(result.intent)
                TunnelResult.Up -> kotlinx.coroutines.withTimeoutOrNull(TRANSPORT_WAIT_MS) {
                    while (!monitor.transportActive) kotlinx.coroutines.delay(100)
                }
                is TunnelResult.Unavailable -> Unit
            }
        }
        return if (monitor.check()) ConnectOutcome.Connected else ConnectOutcome.Failed
    }

    suspend fun disconnect() = tunnel.down()

    private companion object {
        const val TRANSPORT_WAIT_MS = 4_000L
    }
}

/** 공식 WireGuard 앱 실행. 설치되어 있지 않으면 스토어로 보낸다. */
object WireGuardApp {
    private const val PACKAGE = "com.wireguard.android"

    fun open(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, "market://details?id=$PACKAGE".toUri())
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$PACKAGE".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val guideSteps: List<Pair<String, String>> = listOf(
        "WireGuard 실행 확인" to "휴대폰에 설치된 WireGuard 앱이 실행 중인지 확인하세요.",
        "SECURE CAM 터널 활성화" to "SECURE CAM 터널을 선택하고 스위치를 켜서 연결하세요. 앱이 분할 터널에서 제외되어 있으면 서버에 닿지 못하므로, 터널의 애플리케이션 설정에서 SECURE CAM이 제외되지 않았는지 확인하세요.",
        "VPN 주소 확인" to "터널이 켜지면 휴대폰에 10.0.2.x 주소(${ApiConfig.VPN_CLIENT_SUBNET})가 할당됩니다. 다른 주소이거나 주소가 없으면 VPN 설정 파일을 다시 받아 적용하세요.",
        "네트워크 확인" to "인터넷 연결이 정상인지, 회사/공용 네트워크에서 VPN 포트(UDP ${ApiConfig.VPN_PORT})가 차단되지 않았는지 확인하세요.",
        "연결 다시 확인" to "아래 [연결 다시 확인] 버튼을 눌러 내부 서버 연결을 재확인하세요."
    )
}
