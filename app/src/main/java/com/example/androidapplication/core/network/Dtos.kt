package com.example.androidapplication.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject

// 응답 필드가 빠져도 죽지 않도록 모든 필드에 기본값을 둔다

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ErrorBody(
    @SerialName("error_code") @JsonNames("code") val errorCode: String? = null,
    val message: String? = null,
    val details: JsonElement? = null
)

@Serializable data class MessageResponse(val message: String? = null)

/* 2.1 인증 */
@Serializable
data class LoginRequest(val email: String, val password: String, @SerialName("auto_login") val autoLogin: Boolean)

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val email: String = ""
)

@Serializable data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class FindIdRequest(
    val name: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("verification_code") val verificationCode: String
)

@Serializable data class FindIdResponse(val email: String = "")

@Serializable
data class ResetPasswordRequest(
    val email: String,
    @SerialName("verification_code") val verificationCode: String,
    @SerialName("new_password") val newPassword: String
)

@Serializable
data class SessionDto(
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("client_name") val clientName: String = "",
    @SerialName("ip_address") val ipAddress: String = "",
    @SerialName("last_active_at") val lastActiveAt: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false
)

@Serializable data class RevokeOthersResponse(@SerialName("revoked_count") val revokedCount: Int = 0)
@Serializable data class DeleteAccountRequest(val password: String, val reason: String? = null)
@Serializable data class ChangePasswordRequest(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String
)
@Serializable data class UpdateProfileRequest(val name: String)

/* 2.2 보드 */
@Serializable
data class DeviceDto(
    @SerialName("device_id") val deviceId: String? = null,
    val mac: String = "",
    val name: String = "",
    val location: String = "",
    val description: String? = null,
    val status: String = "offline",
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("firmware_version") val firmwareVersion: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("vpn_status") val vpnStatus: String? = null,
    @SerialName("vpn_ip") val vpnIp: String? = null,
    @SerialName("latest_event_time") val latestEventTime: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("webrtc_url") val webrtcUrl: String? = null,
    @SerialName("webrtc_url_sub") val webrtcUrlSub: String? = null
)

@Serializable data class RegisterDeviceRequest(val mac: String, val name: String? = null, val location: String)

@Serializable
data class DevicePatchRequest(val name: String? = null, val location: String? = null, val description: String? = null)

@Serializable
data class DeviceStatusDto(
    val status: String? = null,
    @SerialName("vpn_status") val vpnStatus: String? = null,
    @SerialName("vpn_ip") val vpnIp: String? = null,
    @SerialName("last_handshake") val lastHandshake: String? = null,
    @SerialName("uptime_seconds") val uptimeSeconds: Long? = null,
    @SerialName("storage_total_gb") val storageTotalGb: Double? = null,
    @SerialName("storage_used_gb") val storageUsedGb: Double? = null,
    @SerialName("rtsp_url") val rtspUrl: String? = null,
    @SerialName("rtsp_url_sub") val rtspUrlSub: String? = null
)

/* 2.3 이벤트 */
@Serializable
data class EventDto(
    @SerialName("event_id") val eventId: String = "",
    @SerialName("occurred_at") val occurredAt: String = "",
    val mac: String = "",
    @SerialName("device_name") val deviceName: String = "",
    val location: String? = null,
    @SerialName("event_type") val eventType: String = "",
    val status: String = "unconfirmed",
    val description: String? = null,
    @SerialName("snapshot_url") val snapshotUrl: String? = null,
    @SerialName("clip_url") val clipUrl: String? = null,
    @SerialName("sound_level_db") val soundLevelDb: Double? = null,
    @SerialName("zone_id") val zoneId: String? = null,
    val memo: String? = null,
    @SerialName("is_important") val isImportant: Boolean = false
)

@Serializable
data class EventListDto(
    val total: Int = 0,
    val page: Int = 1,
    val size: Int = 0,
    val items: List<EventDto> = emptyList()
)

@Serializable
data class EventPatchRequest(
    val status: String? = null,
    @SerialName("is_important") val isImportant: Boolean? = null,
    val memo: String? = null
)

/* 2.5 VPN */
@Serializable data class ClientVpnStatusDto(val connected: Boolean = false, @SerialName("client_ip") val clientIp: String? = null)

@Serializable
data class VpnConnectionDto(
    val mac: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("vpn_status") val vpnStatus: String = "disconnected",
    @SerialName("vpn_ip") val vpnIp: String? = null,
    @SerialName("vpn_server") val vpnServer: String? = null,
    @SerialName("connected_duration_sec") val connectedDurationSec: Long? = null,
    @SerialName("last_handshake") val lastHandshake: String? = null,
    @SerialName("error_type") val errorType: String? = null,
    @SerialName("recovery_status") val recoveryStatus: String? = null
)

@Serializable data class VpnActionRequest(val action: String)

@Serializable
data class VpnClientRegisterRequest(
    @SerialName("wg_pubkey") val wgPubkey: String,
    @SerialName("client_name") val clientName: String
)

@Serializable
data class VpnClientRegisterResponse(
    @SerialName("vpn_ip") val vpnIp: String = "",
    @SerialName("server_pubkey") val serverPubkey: String = "",
    @SerialName("server_endpoint") val serverEndpoint: String = "",
    @SerialName("allowed_ips") val allowedIps: JsonElement? = null
)

/* 일반 · 알림 설정 */
@Serializable
data class GeneralSettingsDto(
    val autoRefresh: Boolean = true,
    val showOfflineBoards: Boolean = true,
    val defaultChannel: Int = 2
)

@Serializable data class DeviceAlertDto(val mac: String = "", val enabled: Boolean = true)

@Serializable
data class NotificationSettingsDto(
    @SerialName("motion_alert") val motion: Boolean = true,
    @SerialName("sound_alert") val sound: Boolean = true,
    @SerialName("offline_alert") val offline: Boolean = false,
    @SerialName("reconnect_alert") val reconnect: Boolean = false,
    @SerialName("important_only") val importantOnly: Boolean = false,
    @SerialName("device_alerts") val deviceAlerts: List<DeviceAlertDto> = emptyList()
)

/* 2.4 클라우드 저장소 */
@Serializable
data class StorageSettingsDto(
    @SerialName("auto_save_enabled") val autoSave: Boolean = false,
    @SerialName("video_quality") val videoQuality: String = "FHD",
    @SerialName("retention_days") val retentionDays: Int = 30,
    @SerialName("storage_total_gb") val totalGb: Double = 0.0,
    @SerialName("storage_used_gb") val usedGb: Double = 0.0
)

@Serializable
data class StorageSettingsRequest(
    @SerialName("auto_save_enabled") val autoSave: Boolean,
    @SerialName("retention_days") val retentionDays: Int,
    @SerialName("video_quality") val videoQuality: String
)

@Serializable data class StorageFileDeleteRequest(@SerialName("file_ids") val fileIds: List<String>)

/* 2.6 프라이버시 존 */
@Serializable
data class ZoneCoordinatesDto(val x1: Float = 0f, val y1: Float = 0f, val x2: Float = 0f, val y2: Float = 0f)

@Serializable
data class PrivacyZoneDto(
    @SerialName("zone_id") val zoneId: String = "",
    @SerialName("zone_name") val zoneName: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    val color: String? = null,
    val coordinates: ZoneCoordinatesDto = ZoneCoordinatesDto()
)

@Serializable
data class PrivacyZoneBody(
    @SerialName("zone_name") val zoneName: String,
    @SerialName("is_active") val isActive: Boolean,
    val color: String? = null,
    val coordinates: ZoneCoordinatesDto
)

@Serializable data class ZoneDeleteRequest(@SerialName("zone_ids") val zoneIds: List<String>)

/* 구독 · 결제 [명세 없음] */
@Serializable
data class SubscriptionDto(
    val plan: String = "",
    val status: String = "",
    val paymentStatus: String? = null,
    val nextPaymentDate: String? = null,
    val autoRenew: Boolean = false
)

@Serializable data class PlanChangeRequest(val planId: String)
@Serializable data class AutoRenewRequest(val enabled: Boolean)

@Serializable
data class PaymentMethodDto(
    val id: String = "",
    val type: String = "card",
    val brand: String = "",
    val last4: String = "",
    val expiry: String = "",
    val isDefault: Boolean = false
)

/** 배열 또는 { keys: [...] } 응답을 모두 읽는다. */
fun JsonElement.listUnder(vararg keys: String): JsonArray = when (this) {
    is JsonArray -> this
    is JsonObject -> keys.firstNotNullOfOrNull { this[it] as? JsonArray } ?: JsonArray(emptyList())
    else -> JsonArray(emptyList())
}
