package com.example.androidapplication.core.data

import com.example.androidapplication.core.common.FeatureDisabledError
import com.example.androidapplication.core.common.LIST_TIMEOUT_MS
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.config.FeatureFlags
import com.example.androidapplication.core.network.AccountApi
import com.example.androidapplication.core.network.ApiCaller
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.network.AutoRenewRequest
import com.example.androidapplication.core.network.ChangePasswordRequest
import com.example.androidapplication.core.network.DeleteAccountRequest
import com.example.androidapplication.core.network.DeviceAlertDto
import com.example.androidapplication.core.network.DeviceApi
import com.example.androidapplication.core.network.DeviceDto
import com.example.androidapplication.core.network.DevicePatchRequest
import com.example.androidapplication.core.network.EventApi
import com.example.androidapplication.core.network.EventDto
import com.example.androidapplication.core.network.EventPatchRequest
import com.example.androidapplication.core.network.FindIdRequest
import com.example.androidapplication.core.network.LoginRequest
import com.example.androidapplication.core.network.NotificationSettingsDto
import com.example.androidapplication.core.network.PlanChangeRequest
import com.example.androidapplication.core.network.PublicApi
import com.example.androidapplication.core.network.RegisterDeviceRequest
import com.example.androidapplication.core.network.RegisterRequest
import com.example.androidapplication.core.network.ResetPasswordRequest
import com.example.androidapplication.core.network.SettingsApi
import com.example.androidapplication.core.network.StorageSettingsRequest
import com.example.androidapplication.core.network.SubscriptionApi
import com.example.androidapplication.core.network.SubscriptionDto
import com.example.androidapplication.core.network.UpdateProfileRequest
import com.example.androidapplication.core.network.VpnApi
import com.example.androidapplication.core.network.VpnConnectionDto
import com.example.androidapplication.core.network.listUnder
import com.example.androidapplication.core.session.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

class RemoteAuthRepository @Inject constructor(
    private val publicApi: PublicApi,
    private val accountApi: AccountApi,
    private val caller: ApiCaller
) : AuthRepository {
    override val canFindId get() = FeatureFlags.findId
    override val canResetPassword get() = FeatureFlags.resetPassword
    override val canManageSessions get() = FeatureFlags.loginSessions
    override val canDeleteAccount get() = FeatureFlags.accountDeletion
    override val canChangePassword get() = FeatureFlags.passwordChange
    override val canUpdateProfile get() = FeatureFlags.profileUpdate

    override suspend fun login(email: String, password: String, autoLogin: Boolean): Session {
        val r = caller.call(
            fallback = "로그인 처리 중 문제가 발생했습니다.",
            statusFallback = mapOf(
                400 to "이메일 또는 비밀번호가 올바르지 않습니다. 입력한 정보를 다시 확인해 주세요.",
                429 to "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."
            )
        ) { publicApi.login(LoginRequest(email, password, autoLogin)) }
        return Session(r.accessToken, r.userId, r.name, r.email.ifEmpty { email })
    }

    override suspend fun register(email: String, password: String, name: String) {
        caller.call(
            fallback = "회원가입 처리 중 문제가 발생했습니다.",
            statusFallback = mapOf(409 to "이미 가입된 이메일입니다.")
        ) { publicApi.register(RegisterRequest(email, password, name)) }
    }

    override suspend fun findId(name: String, phone: String, code: String): String =
        caller.call("아이디 찾기에 실패했습니다.") {
            publicApi.findId(FindIdRequest(name, phone, code)).email
        }

    override suspend fun resetPassword(email: String, code: String, newPassword: String): String? =
        caller.call(
            fallback = "비밀번호 재설정에 실패했습니다.",
            statusFallback = mapOf(
                400 to "인증 코드가 유효하지 않거나 만료되었습니다.",
                404 to "등록되지 않은 이메일입니다."
            )
        ) { publicApi.resetPassword(ResetPasswordRequest(email, code, newPassword)).message }

    override suspend fun logout() {
        caller.call("로그아웃 처리에 실패했습니다.", timeoutMs = LIST_TIMEOUT_MS) { accountApi.logout() }
    }

    override suspend fun sessions(): List<LoginSession> =
        caller.call("로그인 기기 목록 조회에 실패했습니다.") {
            accountApi.sessions().map {
                LoginSession(it.sessionId, it.clientName, it.ipAddress, it.lastActiveAt, it.isCurrent)
            }
        }

    override suspend fun revokeSession(id: String) {
        caller.call(
            fallback = "기기 로그아웃에 실패했습니다.",
            statusFallback = mapOf(404 to "이미 로그아웃된 기기입니다.")
        ) { accountApi.revoke(id) }
    }

    override suspend fun revokeOtherSessions(): Int =
        caller.call("다른 기기 로그아웃에 실패했습니다.") { accountApi.revokeOthers().revokedCount }

    override suspend fun deleteAccount(password: String, reason: String?) {
        if (!canDeleteAccount) throw FeatureDisabledError()
        caller.call(
            fallback = "회원 탈퇴 처리에 실패했습니다.",
            statusFallback = mapOf(400 to "비밀번호가 올바르지 않습니다.")
        ) { accountApi.deleteAccount(DeleteAccountRequest(password, reason)) }
    }

    override suspend fun changePassword(current: String, new: String) {
        if (!canChangePassword) throw FeatureDisabledError()
        caller.call("비밀번호 변경에 실패했습니다.") { accountApi.changePassword(ChangePasswordRequest(current, new)) }
    }

    override suspend fun updateProfile(name: String) {
        if (!canUpdateProfile) throw FeatureDisabledError()
        caller.call("사용자 정보 변경에 실패했습니다.") { accountApi.updateProfile(UpdateProfileRequest(name)) }
    }
}

fun DeviceDto.toCamera() = Camera(
    deviceId = deviceId?.takeIf { it.isNotBlank() } ?: Mac.deviceIdFrom(mac),
    mac = Mac.normalize(mac),
    name = name,
    location = location,
    description = description,
    status = status,
    lastSeen = lastSeen,
    firmware = firmwareVersion,
    serial = serialNumber,
    vpnStatus = vpnStatus,
    vpnIp = vpnIp,
    latestEventTime = latestEventTime,
    thumbnailUrl = thumbnailUrl,
    webrtcUrl = webrtcUrl,
    webrtcUrlSub = webrtcUrlSub
)

/** 배열 또는 { devices|items: [...] } 응답을 읽는다. */
fun parseDevices(body: JsonElement): List<Camera> = AppJson
    .decodeFromJsonElement(ListSerializer(DeviceDto.serializer()), body.listUnder("devices", "items"))
    .map(DeviceDto::toCamera)

fun parseVpnConnections(body: JsonElement): List<VpnDevice> = AppJson
    .decodeFromJsonElement(ListSerializer(VpnConnectionDto.serializer()), body.listUnder("connections", "items"))
    .map {
        VpnDevice(
            mac = Mac.normalize(it.mac), name = it.deviceName, serial = it.serialNumber,
            vpnStatus = it.vpnStatus, vpnIp = it.vpnIp, server = it.vpnServer,
            durationSec = it.connectedDurationSec, lastHandshake = it.lastHandshake,
            error = it.errorType, recoveryStatus = it.recoveryStatus
        )
    }

@Singleton
class RemoteDeviceRepository @Inject constructor(
    private val api: DeviceApi,
    private val caller: ApiCaller
) : DeviceRepository {
    private val store = ListStore<Camera>()
    override val cameras = store.state
    override val canUpdate get() = FeatureFlags.deviceUpdate

    override suspend fun refresh(silent: Boolean) = silentlyIf(silent) {
        store.refresh {
            caller.call("카메라 목록 조회에 실패했습니다.", timeoutMs = LIST_TIMEOUT_MS) { parseDevices(api.devices()) }
        }
    }

    override suspend fun register(mac: String, name: String, location: String) {
        caller.call("카메라 등록에 실패했습니다.") {
            api.register(RegisterDeviceRequest(Mac.normalize(mac), name.ifBlank { null }, location))
        }
        refresh(silent = true)
    }

    override suspend fun update(mac: String, name: String?, location: String?, description: String?) {
        if (!canUpdate) throw FeatureDisabledError()
        caller.call("카메라 정보 수정에 실패했습니다.") {
            api.update(mac, DevicePatchRequest(name, location, description))
        }
        refresh(silent = true)
    }

    override suspend fun status(mac: String): DeviceStatus? {
        if (!FeatureFlags.deviceStatus) return null
        return caller.call("카메라 상태 조회에 실패했습니다.") {
            api.status(mac).let { DeviceStatus(it.uptimeSeconds, it.lastHandshake) }
        }
    }

    override fun clear() = store.clear()
}

fun EventDto.toEvent() = EventItem(
    id = eventId, occurredAt = occurredAt, mac = Mac.normalize(mac), cameraName = deviceName,
    location = location, type = eventType, status = status, description = description,
    snapshotUrl = snapshotUrl, videoUrl = clipUrl, soundLevelDb = soundLevelDb, zoneId = zoneId,
    memo = memo, isImportant = isImportant
)

class RemoteEventRepository @Inject constructor(
    private val api: EventApi,
    private val caller: ApiCaller
) : EventRepository {
    override val canUpdate get() = FeatureFlags.eventUpdate

    override suspend fun list(query: EventQuery): EventPage {
        if (!FeatureFlags.eventList) return EventPage(0, query.page, emptyList())
        val params = buildMap {
            put("page", query.page.toString())
            put("size", query.size.toString())
            query.startDate?.let { put("start_date", it) }
            query.endDate?.let { put("end_date", it) }
            query.mac?.let { put("mac", it) }
            query.eventType?.let { put("event_type", it) }
            query.status?.let { put("status", it) }
        }
        val body = caller.call("이벤트 조회 실패") { api.events(params) }
        return EventPage(body.total, body.page, body.items.map(EventDto::toEvent))
    }

    override suspend fun update(id: String, status: String?, important: Boolean?, memo: String?) {
        if (!canUpdate) throw FeatureDisabledError()
        caller.call("이벤트 상태 변경 실패") { api.update(id, EventPatchRequest(status, important, memo)) }
    }
}

@Singleton
class RemoteVpnRepository @Inject constructor(
    private val api: VpnApi,
    private val caller: ApiCaller
) : VpnRepository {
    private val store = ListStore<VpnDevice>()
    override val devices = store.state

    override suspend fun refresh(silent: Boolean) = silentlyIf(silent) {
        store.refresh {
            if (!FeatureFlags.vpnConnections) return@refresh emptyList()
            caller.call("VPN 장치 목록 조회 실패", timeoutMs = LIST_TIMEOUT_MS) { parseVpnConnections(api.connections()) }
        }
    }

    override suspend fun recover(mac: String) {
        caller.call("VPN 복구 요청 실패") { api.recover(mac) }
        refresh(silent = true)
    }

    override fun clear() = store.clear()
}

@Singleton
class RemoteSettingsRepository @Inject constructor(
    private val api: SettingsApi,
    private val caller: ApiCaller
) : SettingsRepository {
    private val _general = MutableStateFlow(GeneralSettings())
    override val general: StateFlow<GeneralSettings> = _general.asStateFlow()

    override suspend fun loadGeneral() {
        val dto = caller.call("일반 설정 정보 조회 실패") { api.general() }
        _general.value = GeneralSettings(dto.autoRefresh, dto.showOfflineBoards, dto.defaultChannel.takeIf { it in setOf(2, 4, 6) } ?: 2)
    }

    override suspend fun updateGeneral(next: GeneralSettings) {
        val previous = _general.value
        _general.value = next
        try {
            caller.call("설정 업데이트 실패") {
                if (next.autoRefresh != previous.autoRefresh) api.updateGeneral("autoRefresh", valueBody(JsonPrimitive(next.autoRefresh)))
                if (next.showOfflineBoards != previous.showOfflineBoards) api.updateGeneral("showOfflineBoards", valueBody(JsonPrimitive(next.showOfflineBoards)))
                if (next.defaultChannel != previous.defaultChannel) api.updateGeneral("defaultChannel", valueBody(JsonPrimitive(next.defaultChannel)))
            }
        } catch (e: Exception) {
            _general.value = previous
            throw e
        }
    }

    private fun valueBody(value: JsonPrimitive) = JsonObject(mapOf("value" to value))

    override suspend fun notifications(): NotificationSettings {
        val dto = caller.call("알림 설정 정보 조회 실패") { api.notifications() }
        return NotificationSettings(
            dto.motion, dto.sound, dto.offline, dto.reconnect, dto.importantOnly,
            dto.deviceAlerts.map { DeviceAlert(Mac.normalize(it.mac), it.enabled) }
        )
    }

    override suspend fun saveNotifications(settings: NotificationSettings) {
        caller.call("설정 변경 실패") {
            api.updateNotifications(
                NotificationSettingsDto(
                    settings.motion, settings.sound, settings.offline, settings.reconnect, settings.importantOnly,
                    settings.deviceAlerts.map { DeviceAlertDto(it.mac, it.enabled) }
                )
            )
        }
    }
}

class RemoteCloudStorageRepository @Inject constructor(
    private val api: SettingsApi,
    private val caller: ApiCaller
) : CloudStorageRepository {
    override val enabled get() = FeatureFlags.cloudStorage

    override suspend fun get(): CloudStorageSettings {
        if (!enabled) throw FeatureDisabledError()
        val dto = caller.call("저장소 설정 조회 실패") { api.storage() }
        return CloudStorageSettings(
            autoSave = dto.autoSave, quality = CloudQuality.fromServer(dto.videoQuality),
            retentionDays = dto.retentionDays, totalGb = dto.totalGb, usedGb = dto.usedGb
        )
    }

    override suspend fun save(settings: CloudStorageSettings): CloudStorageSettings {
        if (!enabled) throw FeatureDisabledError()
        caller.call("저장소 설정 변경 실패") {
            api.updateStorage(StorageSettingsRequest(settings.autoSave, settings.retentionDays, settings.quality.serverValue))
        }
        // 저장 방식과 덮어쓰기는 명세에 없어서 화면 값을 유지한다
        return get().copy(eventMode = settings.eventMode, overwriteWhenFull = settings.overwriteWhenFull)
    }
}

class RemoteSubscriptionRepository @Inject constructor(
    private val api: SubscriptionApi,
    private val caller: ApiCaller
) : SubscriptionRepository {
    override val enabled get() = FeatureFlags.subscription
    override val paymentsEnabled get() = FeatureFlags.paymentMethods

    private fun SubscriptionDto.toInfo() = SubscriptionInfo(plan, status, nextPaymentDate, autoRenew)

    override suspend fun get(): SubscriptionInfo {
        if (!enabled) throw FeatureDisabledError()
        return caller.call("구독 정보 조회 실패") { api.subscription().toInfo() }
    }

    override suspend fun changePlan(planId: String): SubscriptionInfo {
        if (!enabled) throw FeatureDisabledError()
        return caller.call("요금제 변경 실패") { api.changePlan(PlanChangeRequest(planId)).toInfo() }
    }

    override suspend fun setAutoRenew(enabled: Boolean) {
        if (!this.enabled) throw FeatureDisabledError()
        caller.call("자동 갱신 변경 실패") { api.autoRenew(AutoRenewRequest(enabled)) }
    }

    override suspend fun paymentMethods(): List<PaymentMethod> {
        if (!paymentsEnabled) throw FeatureDisabledError()
        return caller.call("결제 수단 조회 실패") {
            api.paymentMethods().map { PaymentMethod(it.id, it.brand, it.last4, it.expiry, it.isDefault) }
        }
    }

    override suspend fun setDefaultPayment(id: String) {
        if (!paymentsEnabled) throw FeatureDisabledError()
        caller.call("기본 결제 수단 변경 실패") { api.setDefault(id) }
    }

    override suspend fun deletePayment(id: String) {
        if (!paymentsEnabled) throw FeatureDisabledError()
        caller.call("결제 수단 삭제 실패") { api.delete(id) }
    }
}
