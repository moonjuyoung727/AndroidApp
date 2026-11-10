package com.example.androidapplication.core.fake

import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.data.AuthRepository
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.CloudStorageRepository
import com.example.androidapplication.core.data.CloudStorageSettings
import com.example.androidapplication.core.data.DeviceAlert
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.DeviceStatus
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.EventPage
import com.example.androidapplication.core.data.EventQuery
import com.example.androidapplication.core.data.EventRepository
import com.example.androidapplication.core.data.EventStatus
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.data.GeneralSettings
import com.example.androidapplication.core.data.ListStore
import com.example.androidapplication.core.data.LoginSession
import com.example.androidapplication.core.data.NotificationSettings
import com.example.androidapplication.core.data.PaymentMethod
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.data.SubscriptionInfo
import com.example.androidapplication.core.data.SubscriptionPlans
import com.example.androidapplication.core.data.SubscriptionRepository
import com.example.androidapplication.core.data.VpnDevice
import com.example.androidapplication.core.data.VpnRepository
import com.example.androidapplication.core.session.Session
import com.example.androidapplication.core.vpn.ClientVpnProbe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/* debug 빌드 전용. release 소스셋에는 이 파일이 없다. */

@Singleton
class FakeClientVpnProbe @Inject constructor() : ClientVpnProbe {
    override suspend fun isConnected(transportActive: Boolean) = true
}

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    override val canFindId = true
    override val canResetPassword = true
    override val canManageSessions = true
    override val canDeleteAccount = true
    override val canChangePassword = true
    override val canUpdateProfile = true
    private val sessions = mutableListOf(
        LoginSession("s1", "Galaxy S23", "10.0.2.11", Instant.now().toString(), true),
        LoginSession("s2", "Chrome (Windows)", "10.0.2.21", Instant.now().minus(2, ChronoUnit.HOURS).toString(), false)
    )

    override suspend fun login(email: String, password: String, autoLogin: Boolean): Session {
        delay(300)
        if (password == "wrongpass") throw ApiError(400, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.")
        return Session("fake-token-$email", "fake-${email.hashCode()}", email.substringBefore('@'), email)
    }

    override suspend fun register(email: String, password: String, name: String) {
        if (email.startsWith("dup")) throw ApiError(409, null, "이미 가입된 이메일입니다.")
    }

    override suspend fun findId(name: String, phone: String, code: String) = "te***@example.com"
    override suspend fun resetPassword(email: String, code: String, newPassword: String) = "비밀번호가 변경되었습니다."
    override suspend fun logout() = Unit
    override suspend fun sessions() = sessions.toList()
    override suspend fun revokeSession(id: String) { sessions.removeAll { it.id == id } }
    override suspend fun revokeOtherSessions(): Int {
        val count = sessions.count { !it.isCurrent }
        sessions.removeAll { !it.isCurrent }
        return count
    }
    override suspend fun deleteAccount(password: String, reason: String?) = Unit
    override suspend fun changePassword(current: String, new: String) = Unit
    override suspend fun updateProfile(name: String) = Unit
}

@Singleton
class FakeDeviceRepository @Inject constructor() : DeviceRepository {
    private val store = ListStore<Camera>()
    override val cameras = store.state
    override val canUpdate = true
    private var data = List(9) { i ->
        val mac = "00000000AB%02X".format(i + 1)
        Camera(
            deviceId = Mac.deviceIdFrom(mac), mac = mac, name = "카메라 ${i + 1}",
            location = listOf("출입구", "주차장", "사무실")[i % 3],
            status = if (i % 4 == 3) "offline" else "online",
            lastSeen = Instant.now().minusSeconds(i * 40L).toString(),
            firmware = "1.2.$i", serial = "SN-2026-%04d".format(i + 1),
            vpnStatus = listOf("connected", "connected", "disconnected", "error")[i % 4],
            vpnIp = "10.0.1.${101 + i}"
        )
    }

    override suspend fun refresh(silent: Boolean) = store.refresh { delay(150); data }

    override suspend fun register(mac: String, name: String, location: String) {
        if (data.any { it.mac == Mac.normalize(mac) }) throw ApiError(409, null, "이미 등록된 카메라입니다.")
        data = data + Camera(Mac.deviceIdFrom(mac), Mac.normalize(mac), name, location, status = "offline")
        refresh(true)
    }

    override suspend fun update(mac: String, name: String?, location: String?, description: String?) {
        data = data.map {
            if (it.mac == mac) it.copy(name = name ?: it.name, location = location ?: it.location, description = description ?: it.description) else it
        }
        refresh(true)
    }

    override suspend fun status(mac: String) = DeviceStatus(302_640, Instant.now().toString())
    override fun clear() = store.clear()
}

@Singleton
class FakeEventRepository @Inject constructor() : EventRepository {
    override val canUpdate = true
    private val types = EventTypes.labels.keys.toList()
    private var events = List(150) { i ->
        val mac = "00000000AB%02X".format(i % 9 + 1)
        EventItem(
            id = "${1000 + i}", occurredAt = Instant.now().minus(i * 47L, ChronoUnit.MINUTES).toString(),
            mac = mac, cameraName = "카메라 ${i % 9 + 1}", location = listOf("출입구", "주차장", "사무실")[i % 3],
            type = types[i % types.size], status = if (i % 3 == 0) EventStatus.UNCONFIRMED else EventStatus.CONFIRMED,
            description = "${EventTypes.label(types[i % types.size])} 이벤트입니다.", soundLevelDb = if (i % 6 == 1) 62.5 else null
        )
    }

    override suspend fun list(query: EventQuery): EventPage {
        delay(150)
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val filtered = events.filter { e ->
            val date = Instant.parse(e.occurredAt).atZone(zone).toLocalDate().toString()
            (query.startDate == null || date >= query.startDate!!) && (query.endDate == null || date <= query.endDate!!) &&
                (query.mac == null || e.mac == query.mac) && (query.eventType == null || e.type == query.eventType) &&
                (query.status == null || e.status == query.status)
        }
        return EventPage(filtered.size, query.page, filtered.drop((query.page - 1) * query.size).take(query.size))
    }

    override suspend fun update(id: String, status: String?, important: Boolean?, memo: String?) {
        events = events.map {
            if (it.id == id) it.copy(status = status ?: it.status, isImportant = important ?: it.isImportant, memo = memo ?: it.memo) else it
        }
    }
}

@Singleton
class FakeVpnRepository @Inject constructor() : VpnRepository {
    private val store = ListStore<VpnDevice>()
    override val devices = store.state
    private var data = List(9) { i ->
        val status = listOf("connected", "connected", "disconnected", "error")[i % 4]
        VpnDevice(
            "00000000AB%02X".format(i + 1), "카메라 ${i + 1}", "SN-2026-%04d".format(i + 1), status, "10.0.1.${101 + i}",
            "10.0.0.1", 302_640L, Instant.now().minusSeconds(30).toString(),
            if (status == "error") "Handshake Timeout" else null, null
        )
    }

    override suspend fun refresh(silent: Boolean) = store.refresh { delay(150); data }

    override suspend fun recover(mac: String) {
        data = data.map { if (it.mac == mac) it.copy(recoveryStatus = "recovering") else it }
        refresh(true)
    }

    override fun clear() = store.clear()
}

@Singleton
class FakeSettingsRepository @Inject constructor() : SettingsRepository {
    private val _general = MutableStateFlow(GeneralSettings())
    override val general: StateFlow<GeneralSettings> = _general
    private var notifications = NotificationSettings(
        deviceAlerts = List(3) { DeviceAlert("00000000AB%02X".format(it + 1), true) }
    )

    override suspend fun loadGeneral() = Unit
    override suspend fun updateGeneral(next: GeneralSettings) { _general.value = next }
    override suspend fun notifications() = notifications
    override suspend fun saveNotifications(settings: NotificationSettings) { notifications = settings }
}

@Singleton
class FakeCloudStorageRepository @Inject constructor() : CloudStorageRepository {
    override val enabled = true
    private var settings = CloudStorageSettings(autoSave = true, totalGb = 200.0, usedGb = 128.4)
    override suspend fun get() = settings
    override suspend fun save(settings: CloudStorageSettings): CloudStorageSettings { this.settings = settings; return settings }
}

@Singleton
class FakeSubscriptionRepository @Inject constructor() : SubscriptionRepository {
    override val enabled = true
    override val paymentsEnabled = true
    private var info = SubscriptionInfo("Standard", "active", "2026-12-10", true)
    private var methods = listOf(
        PaymentMethod("pm-1", "VISA", "4242", "12/28", true),
        PaymentMethod("pm-2", "Mastercard", "5100", "03/27", false)
    )

    override suspend fun get() = info
    override suspend fun changePlan(planId: String): SubscriptionInfo {
        info = info.copy(plan = SubscriptionPlans.all.first { it.id == planId }.name)
        return info
    }
    override suspend fun setAutoRenew(enabled: Boolean) { info = info.copy(autoRenew = enabled) }
    override suspend fun paymentMethods() = methods
    override suspend fun setDefaultPayment(id: String) { methods = methods.map { it.copy(isDefault = it.id == id) } }
    override suspend fun deletePayment(id: String) { methods = methods.filterNot { it.id == id } }
}
