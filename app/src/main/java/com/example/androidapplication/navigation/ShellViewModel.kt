package com.example.androidapplication.navigation

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.data.AuthRepository
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.EventQuery
import com.example.androidapplication.core.data.EventRepository
import com.example.androidapplication.core.data.EventStatus
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.data.SubscriptionRepository
import com.example.androidapplication.core.data.VpnRepository
import com.example.androidapplication.core.session.AuthState
import com.example.androidapplication.core.session.SessionManager
import com.example.androidapplication.core.vpn.ClientVpnConnector
import com.example.androidapplication.core.vpn.ConnectOutcome
import com.example.androidapplication.core.vpn.VpnMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationEntry(val id: String, val camera: String, val type: String, val occurredAt: String)
data class NotificationState(val unread: Int = 0, val recent: List<NotificationEntry> = emptyList())

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val sessions: SessionManager,
    private val authRepository: AuthRepository,
    private val monitor: VpnMonitor,
    private val connector: ClientVpnConnector,
    private val deviceRepository: DeviceRepository,
    private val vpnRepository: VpnRepository,
    private val eventRepository: EventRepository,
    private val settingsRepository: SettingsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    bus: UiMessageBus
) : ViewModel() {
    val auth: StateFlow<AuthState> = sessions.state
    val vpn = monitor.ui
    val messages = bus.messages

    private val _notifications = MutableStateFlow(NotificationState())
    val notifications = _notifications.asStateFlow()

    private val _planName = MutableStateFlow<String?>(null)
    val planName = _planName.asStateFlow()

    private val consentChannel = Channel<Intent>(Channel.CONFLATED)
    val consentRequests = consentChannel.receiveAsFlow()

    init {
        viewModelScope.launch { sessions.restore() }
        // 다른 계정의 목록이 남지 않도록 로그아웃 시 공유 캐시를 비운다
        viewModelScope.launch {
            sessions.state.collect { state ->
                if (state == AuthState.LoggedOut) {
                    deviceRepository.clear()
                    vpnRepository.clear()
                    monitor.reset()
                    _notifications.value = NotificationState()
                    _planName.value = null
                }
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            runCatching { settingsRepository.loadGeneral() }
            if (subscriptionRepository.enabled) {
                _planName.value = runCatching { subscriptionRepository.get().plan }.getOrNull()
            }
        }
    }

    suspend fun refreshNotifications() {
        try {
            val page = eventRepository.list(EventQuery(size = 10, status = EventStatus.UNCONFIRMED))
            _notifications.value = NotificationState(
                unread = page.total,
                recent = page.items.map { NotificationEntry(it.id, it.cameraName, it.type, it.occurredAt) }
            )
        } catch (_: AppError) {
            // 자동 재조회의 실패는 알리지 않는다
        }
    }

    fun reconnectVpn() {
        viewModelScope.launch {
            when (val outcome = connector.connectAndCheck()) {
                is ConnectOutcome.NeedsConsent -> consentChannel.trySend(outcome.intent)
                else -> Unit
            }
        }
    }

    fun onVpnConsentResult(granted: Boolean) {
        if (granted) reconnectVpn() else viewModelScope.launch { monitor.check() }
    }

    fun dismissVpnBanner() = monitor.dismissBanner()

    /** 서버 로그아웃이 실패해도 로컬 세션을 지운다. */
    fun logout() {
        viewModelScope.launch {
            runCatching { authRepository.logout() }
            runCatching { connector.disconnect() }
            sessions.clear()
        }
    }
}
