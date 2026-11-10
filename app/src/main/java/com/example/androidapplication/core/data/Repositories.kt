package com.example.androidapplication.core.data

import com.example.androidapplication.core.session.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * 기능마다 인터페이스 하나. Remote 구현은 main에, Fake 구현은 src/debug에 있어 release 빌드에 들어가지 않는다.
 * canXxx가 false면 서버 연동 플래그가 꺼진 기능이며, 화면은 저장되는 것처럼 보이지 않게 비활성으로 표시한다.
 */

interface AuthRepository {
    val canFindId: Boolean
    val canResetPassword: Boolean
    val canManageSessions: Boolean
    val canDeleteAccount: Boolean
    val canChangePassword: Boolean
    val canUpdateProfile: Boolean

    suspend fun login(email: String, password: String, autoLogin: Boolean): Session
    suspend fun register(email: String, password: String, name: String)
    suspend fun findId(name: String, phone: String, code: String): String
    suspend fun resetPassword(email: String, code: String, newPassword: String): String?
    suspend fun logout()
    suspend fun sessions(): List<LoginSession>
    suspend fun revokeSession(id: String)
    suspend fun revokeOtherSessions(): Int
    suspend fun deleteAccount(password: String, reason: String?)
    suspend fun changePassword(current: String, new: String)
    suspend fun updateProfile(name: String)
}

interface DeviceRepository {
    val cameras: StateFlow<ListState<Camera>>
    val canUpdate: Boolean

    suspend fun refresh(silent: Boolean = false)
    suspend fun register(mac: String, name: String, location: String)
    suspend fun update(mac: String, name: String? = null, location: String? = null, description: String? = null)
    suspend fun status(mac: String): DeviceStatus?
    fun clear()
}

interface EventRepository {
    val canUpdate: Boolean
    suspend fun list(query: EventQuery): EventPage
    suspend fun update(id: String, status: String? = null, important: Boolean? = null, memo: String? = null)
}

interface VpnRepository {
    val devices: StateFlow<ListState<VpnDevice>>
    suspend fun refresh(silent: Boolean = false)
    suspend fun recover(mac: String)
    fun clear()
}

interface SettingsRepository {
    val general: StateFlow<GeneralSettings>
    suspend fun loadGeneral()
    /** 낙관적으로 먼저 반영하고, 실패하면 되돌린 뒤 예외를 던진다. */
    suspend fun updateGeneral(next: GeneralSettings)
    suspend fun notifications(): NotificationSettings
    suspend fun saveNotifications(settings: NotificationSettings)
}

interface CloudStorageRepository {
    val enabled: Boolean
    suspend fun get(): CloudStorageSettings
    suspend fun save(settings: CloudStorageSettings): CloudStorageSettings
}

interface SubscriptionRepository {
    val enabled: Boolean
    val paymentsEnabled: Boolean
    suspend fun get(): SubscriptionInfo
    suspend fun changePlan(planId: String): SubscriptionInfo
    suspend fun setAutoRenew(enabled: Boolean)
    suspend fun paymentMethods(): List<PaymentMethod>
    suspend fun setDefaultPayment(id: String)
    suspend fun deletePayment(id: String)
}

interface PrivacyZoneRepository {
    val isServerApplied: Boolean
    fun observe(mac: String): Flow<List<PrivacyZone>>
    /** 저장 직전에 부른다. 존 정보를 얻지 못하면 예외를 던져 저장을 막는다 (fail closed). */
    suspend fun current(mac: String): List<PrivacyZone>
    suspend fun save(mac: String, zones: List<PrivacyZone>, cameraOnline: Boolean)
}
