package com.example.androidapplication.core.data

import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class Camera(
    val deviceId: String,
    val mac: String,
    val name: String,
    val location: String,
    val description: String? = null,
    val status: String = "offline",
    val lastSeen: String? = null,
    val firmware: String? = null,
    val serial: String? = null,
    val vpnStatus: String? = null,
    val vpnIp: String? = null,
    val latestEventTime: String? = null,
    val thumbnailUrl: String? = null,
    val webrtcUrl: String? = null,
    val webrtcUrlSub: String? = null
) {
    val isOnline: Boolean get() = status == "online"
}

data class DeviceStatus(val uptimeSeconds: Long?, val lastHandshake: String?)

enum class ListSource(val label: String) {
    Loading("불러오는 중"),
    Server("서버 데이터"),
    Stale("이전 데이터")
}

data class ListState<T>(
    val items: List<T> = emptyList(),
    val source: ListSource = ListSource.Loading,
    val fetchedAt: Instant? = null,
    val isFetching: Boolean = false
)

/** 목록을 화면끼리 공유한다. 조회가 진행 중이면 새 조회를 건너뛰어 요청이 겹치지 않게 한다. */
class ListStore<T> {
    private val _state = MutableStateFlow(ListState<T>())
    val state: StateFlow<ListState<T>> = _state.asStateFlow()
    private val inFlight = AtomicBoolean(false)

    suspend fun refresh(fetch: suspend () -> List<T>) {
        if (!inFlight.compareAndSet(false, true)) return
        _state.update { it.copy(isFetching = true) }
        try {
            val items = fetch()
            _state.update { it.copy(items = items, source = ListSource.Server, fetchedAt = Instant.now()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(source = ListSource.Stale) }
            throw e
        } finally {
            inFlight.set(false)
            _state.update { it.copy(isFetching = false) }
        }
    }

    fun mutate(transform: (List<T>) -> List<T>) = _state.update { it.copy(items = transform(it.items)) }

    fun clear() {
        _state.value = ListState()
    }
}

/** 자동 재조회의 실패는 Snackbar를 띄우지 않는다. */
inline fun silentlyIf(silent: Boolean, block: () -> Unit) {
    try {
        block()
    } catch (e: AppError) {
        if (!silent) throw e
    }
}

/* ===== 이벤트 ===== */

object EventTypes {
    val labels: Map<String, String> = linkedMapOf(
        "motion" to "움직임 감지",
        "sound" to "소리 감지",
        "camera_connect" to "카메라 연결",
        "camera_disconnect" to "카메라 연결 끊김",
        "vpn_connect" to "VPN 연결",
        "vpn_disconnect" to "VPN 연결 끊김"
    )

    /** 모르는 값이 와도 죽지 않게 원문을 표시한다. */
    fun label(type: String): String = labels[type] ?: type
}

object EventStatus {
    const val UNCONFIRMED = "unconfirmed"
    const val CONFIRMED = "confirmed"
    fun label(status: String): String = when (status) {
        UNCONFIRMED -> "미확인"
        CONFIRMED -> "확인 완료"
        else -> status
    }
}

// 프로세스 재생성에도 상세 화면을 복원할 수 있도록 내비게이션 인자로 직렬화한다
@Serializable
data class EventItem(
    val id: String,
    val occurredAt: String,
    val mac: String,
    val cameraName: String,
    val location: String? = null,
    val type: String,
    val status: String,
    val description: String? = null,
    val snapshotUrl: String? = null,
    val videoUrl: String? = null,
    val soundLevelDb: Double? = null,
    val zoneId: String? = null,
    val memo: String? = null,
    val isImportant: Boolean = false
)

data class EventQuery(
    val page: Int = 1,
    val size: Int = 30,
    val startDate: String? = null,
    val endDate: String? = null,
    val mac: String? = null,
    val eventType: String? = null,
    val status: String? = null
)

data class EventPage(val total: Int, val page: Int, val items: List<EventItem>)

/* ===== VPN ===== */

object VpnStatusText {
    const val CONNECTED = "connected"
    const val DISCONNECTED = "disconnected"
    const val ERROR = "error"
    fun label(status: String?): String = when (status) {
        CONNECTED -> "정상"
        DISCONNECTED -> "연결 끊김"
        ERROR -> "오류"
        null, "" -> "-"
        else -> status
    }
}

enum class RecoveryState(val label: String) { Normal("정상"), Recovering("복구 중"), Failed("복구 실패") }

data class VpnDevice(
    val mac: String,
    val name: String,
    val serial: String?,
    val vpnStatus: String,
    val vpnIp: String?,
    val server: String?,
    val durationSec: Long?,
    val lastHandshake: String?,
    val error: String?,
    val recoveryStatus: String?
) {
    val recovery: RecoveryState
        get() = when (recoveryStatus) {
            "normal", "정상" -> RecoveryState.Normal
            "recovering", "복구 중" -> RecoveryState.Recovering
            "failed", "복구 실패" -> RecoveryState.Failed
            else -> when (vpnStatus) {
                VpnStatusText.CONNECTED -> RecoveryState.Normal
                VpnStatusText.DISCONNECTED -> RecoveryState.Recovering
                else -> RecoveryState.Failed
            }
        }
}

/* ===== 계정 ===== */

data class LoginSession(
    val id: String,
    val clientName: String,
    val ipAddress: String,
    val lastActiveAt: String?,
    val isCurrent: Boolean
)

/* ===== 설정 ===== */

data class GeneralSettings(
    val autoRefresh: Boolean = true,
    val showOfflineBoards: Boolean = true,
    val defaultChannel: Int = 2
)

data class DeviceAlert(val mac: String, val enabled: Boolean)

data class NotificationSettings(
    val motion: Boolean = true,
    val sound: Boolean = true,
    val offline: Boolean = false,
    val reconnect: Boolean = false,
    val importantOnly: Boolean = false,
    val deviceAlerts: List<DeviceAlert> = emptyList()
)

/** 서버 명세에 없어서 이 기기에만 저장하는 알림 설정. */
data class LocalNotificationSettings(
    val vpn: Boolean = true,
    val pushEnabled: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true
)

enum class CloudQuality(val serverValue: String, val label: String, val gbPerDay: Double) {
    Low("SD", "저화질 (480p)", 0.6),
    Medium("HD", "중화질 (720p)", 1.4),
    High("FHD", "고화질 (1080p)", 3.1);

    companion object {
        fun fromServer(value: String?) = entries.firstOrNull { it.serverValue == value } ?: High
    }
}

data class CloudStorageSettings(
    val autoSave: Boolean = false,
    val eventMode: Boolean = true,
    val quality: CloudQuality = CloudQuality.High,
    val retentionDays: Int = 30,
    val overwriteWhenFull: Boolean = false,
    val totalGb: Double = 0.0,
    val usedGb: Double = 0.0
)

data class SubscriptionInfo(
    val plan: String,
    val status: String,
    val nextPaymentDate: String?,
    val autoRenew: Boolean
)

data class SubscriptionPlan(
    val id: String,
    val name: String,
    val price: Int,
    val maxCameras: Int,
    val storageGb: Int,
    val retentionDays: Int,
    val features: List<String>
)

object SubscriptionPlans {
    val all = listOf(
        SubscriptionPlan("basic", "Basic", 9_900, 4, 50, 7, listOf("카메라 최대 4대", "클라우드 50GB", "보관 7일")),
        SubscriptionPlan("standard", "Standard", 19_900, 16, 200, 30, listOf("카메라 최대 16대", "클라우드 200GB", "보관 30일", "이벤트 영상 다운로드")),
        SubscriptionPlan("premium", "Premium", 39_900, 64, 1000, 90, listOf("카메라 최대 64대", "클라우드 1TB", "보관 90일", "우선 기술 지원"))
    )
}

data class PaymentMethod(
    val id: String,
    val brand: String,
    val last4: String,
    val expiry: String,
    val isDefault: Boolean
)

/* ===== 프라이버시 존 ===== */

data class PrivacyZone(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val enabled: Boolean,
    val color: String? = null
)

fun ApiError.isNotFound(): Boolean = status == 404
