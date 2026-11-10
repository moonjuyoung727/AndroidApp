package com.example.androidapplication.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.CAMERA_POLL_MS
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.config.PrivacyZoneLimits
import com.example.androidapplication.core.data.*
import com.example.androidapplication.core.media.MediaLibrary
import com.example.androidapplication.core.media.LocalStorageSettings
import com.example.androidapplication.core.media.StorageUsage
import com.example.androidapplication.core.session.AuthState
import com.example.androidapplication.core.session.SessionManager
import com.example.androidapplication.core.webrtc.StreamQuality
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.StreamUrlResolver
import com.example.androidapplication.core.webrtc.WebRtcEngine
import com.example.androidapplication.core.webrtc.WhepSession
import com.example.androidapplication.core.webrtc.WhepSessionFactory
import com.example.androidapplication.navigation.CameraSettingsRoute
import com.example.androidapplication.navigation.PrivacyZoneRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GeneralSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val bus: UiMessageBus
) : ViewModel() {
    val settings = repository.general

    fun update(next: GeneralSettings) {
        viewModelScope.launch {
            try {
                repository.updateGeneral(next)
            } catch (e: AppError) {
                bus.showError(e, "설정 업데이트 실패")
            }
        }
    }
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val local: LocalPreferences,
    private val bus: UiMessageBus,
    devices: DeviceRepository
) : ViewModel() {
    private val _server = MutableStateFlow(NotificationSettings())
    val server = _server.asStateFlow()
    val localSettings = local.notifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalNotificationSettings())
    val cameras = devices.cameras

    init {
        viewModelScope.launch {
            try {
                _server.value = repository.notifications()
            } catch (e: AppError) {
                bus.showError(e, "알림 설정 정보 조회 실패")
            }
        }
    }

    /** 낙관적 업데이트, 실패 시 되돌림. 서버는 전체 저장만 지원한다. */
    fun update(next: NotificationSettings) {
        val previous = _server.value
        _server.value = next
        viewModelScope.launch {
            try {
                repository.saveNotifications(next)
            } catch (e: AppError) {
                _server.value = previous
                bus.showError(e, "설정 변경 실패")
            }
        }
    }

    fun setDevice(mac: String, enabled: Boolean) {
        val current = _server.value
        val others = current.deviceAlerts.filterNot { it.mac == mac }
        update(current.copy(deviceAlerts = others + DeviceAlert(mac, enabled)))
    }

    fun updateLocal(next: LocalNotificationSettings) {
        viewModelScope.launch { local.saveNotifications(next) }
    }
}

data class SubscriptionUi(
    val loading: Boolean = true,
    val info: SubscriptionInfo? = null,
    val methods: List<PaymentMethod> = emptyList()
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repository: SubscriptionRepository,
    private val bus: UiMessageBus
) : ViewModel() {
    val enabled get() = repository.enabled
    val paymentsEnabled get() = repository.paymentsEnabled
    private val _ui = MutableStateFlow(SubscriptionUi())
    val ui = _ui.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val info = if (enabled) repository.get() else null
                val methods = if (paymentsEnabled) repository.paymentMethods() else emptyList()
                _ui.value = SubscriptionUi(false, info, methods)
            } catch (e: AppError) {
                bus.showError(e, "구독 정보 조회 실패")
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    private fun act(fallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load()
            } catch (e: AppError) {
                bus.showError(e, fallback)
            }
        }
    }

    fun changePlan(id: String) = act("요금제 변경 실패") { repository.changePlan(id) }

    fun setAutoRenew(value: Boolean) {
        val previous = _ui.value.info ?: return
        _ui.update { it.copy(info = previous.copy(autoRenew = value)) }
        viewModelScope.launch {
            try {
                repository.setAutoRenew(value)
            } catch (e: AppError) {
                _ui.update { it.copy(info = previous) }
                bus.showError(e, "자동 갱신 변경 실패")
            }
        }
    }

    fun setDefault(id: String) = act("기본 결제 수단 변경 실패") { repository.setDefaultPayment(id) }
    fun delete(id: String) = act("결제 수단 삭제 실패") { repository.deletePayment(id) }
}

@HiltViewModel
class CloudStorageViewModel @Inject constructor(
    private val repository: CloudStorageRepository,
    private val bus: UiMessageBus
) : ViewModel() {
    val enabled get() = repository.enabled
    private val _saved = MutableStateFlow(CloudStorageSettings())
    private val _draft = MutableStateFlow(CloudStorageSettings())
    val saved = _saved.asStateFlow()
    val draft = _draft.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    init {
        if (enabled) viewModelScope.launch {
            try {
                val data = repository.get()
                _saved.value = data
                _draft.value = data
            } catch (e: AppError) {
                bus.showError(e, "저장소 설정 조회 실패")
            }
        }
    }

    fun edit(transform: (CloudStorageSettings) -> CloudStorageSettings) {
        _draft.value = transform(_draft.value)
    }

    fun save() {
        viewModelScope.launch {
            _saving.value = true
            try {
                val data = repository.save(_draft.value)
                _saved.value = data
                _draft.value = data
                bus.show("저장되었습니다.")
            } catch (e: AppError) {
                bus.showError(e, "저장소 설정 변경 실패")
            } finally {
                _saving.value = false
            }
        }
    }
}

@HiltViewModel
class LocalStorageViewModel @Inject constructor(
    private val prefs: LocalPreferences,
    private val library: MediaLibrary,
    sessions: SessionManager,
    private val bus: UiMessageBus
) : ViewModel() {
    private val owner = sessions.session?.userId.orEmpty()
    val settings = prefs.localStorage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalStorageSettings())
    val usage: StateFlow<StorageUsage> = library.observe(owner)
        .map(library::usage)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUsage(0, 0, 0))

    fun update(next: LocalStorageSettings) {
        viewModelScope.launch {
            prefs.saveLocalStorage(next)
            library.enforcePolicy()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            library.deleteAll(owner)
            bus.show("이 계정의 저장 파일을 모두 삭제했습니다.")
        }
    }
}

@HiltViewModel
class CameraSettingsViewModel @Inject constructor(
    state: SavedStateHandle,
    private val devices: DeviceRepository,
    private val bus: UiMessageBus
) : ViewModel() {
    val initialMac: String? = state.toRoute<CameraSettingsRoute>().mac
    val cameras = devices.cameras
    val canUpdate get() = devices.canUpdate

    init {
        viewModelScope.launch { runCatching { devices.refresh(silent = true) } }
    }

    fun save(mac: String, name: String, description: String) {
        viewModelScope.launch {
            try {
                devices.update(mac, name = name.trim(), description = description.trim())
                bus.show("저장되었습니다.")
            } catch (e: AppError) {
                bus.showError(e, "카메라 정보 수정에 실패했습니다.")
            }
        }
    }
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val sessions: SessionManager,
    private val bus: UiMessageBus
) : ViewModel() {
    val session = sessions.state.map { (it as? AuthState.LoggedIn)?.session }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessions.session)
    val canUpdateProfile get() = repository.canUpdateProfile
    val canChangePassword get() = repository.canChangePassword
    val canDeleteAccount get() = repository.canDeleteAccount
    val canManageSessions get() = repository.canManageSessions
    private val _remember = MutableStateFlow(false)
    val remember = _remember.asStateFlow()

    init {
        viewModelScope.launch { _remember.value = sessions.rememberedLogin().second }
    }

    fun setRemember(value: Boolean) {
        _remember.value = value
        viewModelScope.launch { sessions.setRemember(value) }
    }

    fun updateName(name: String) {
        viewModelScope.launch {
            try {
                repository.updateProfile(name.trim())
                sessions.updateName(name.trim())
                bus.show("사용자 정보를 변경했습니다.")
            } catch (e: AppError) {
                bus.showError(e, "사용자 정보 변경에 실패했습니다.")
            }
        }
    }

    fun changePassword(current: String, next: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.changePassword(current, next)
                bus.show("비밀번호를 변경했습니다.")
                onDone()
            } catch (e: AppError) {
                bus.showError(e, "비밀번호 변경에 실패했습니다.")
            }
        }
    }

    fun deleteAccount(password: String, reason: String?) {
        viewModelScope.launch {
            try {
                repository.deleteAccount(password, reason)
                sessions.clear()
            } catch (e: AppError) {
                bus.showError(e, "회원 탈퇴 처리에 실패했습니다.")
            }
        }
    }
}

@HiltViewModel
class LoginSessionsViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val bus: UiMessageBus
) : ViewModel() {
    private val _sessions = MutableStateFlow<List<LoginSession>>(emptyList())
    val sessions = _sessions.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                _sessions.value = repository.sessions()
            } catch (e: AppError) {
                bus.showError(e, "로그인 기기 목록 조회에 실패했습니다.")
            } finally {
                _loading.value = false
            }
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch {
            try {
                repository.revokeSession(id)
                load()
            } catch (e: AppError) {
                bus.showError(e, "기기 로그아웃에 실패했습니다.")
                load()
            }
        }
    }

    fun revokeOthers() {
        viewModelScope.launch {
            try {
                bus.show("다른 기기 ${repository.revokeOtherSessions()}대를 로그아웃했습니다.")
                load()
            } catch (e: AppError) {
                bus.showError(e, "다른 기기 로그아웃에 실패했습니다.")
            }
        }
    }
}

/** 프라이버시 존 초안 편집. 순수 함수로 두어 단위 테스트한다. */
object ZoneDraft {
    /** 드래그 영역을 0~1로 자르고, 최소 크기 미만이거나 개수를 넘으면 null. */
    fun tryAdd(zones: List<PrivacyZone>, x1: Float, y1: Float, x2: Float, y2: Float, idFactory: () -> String): List<PrivacyZone>? {
        if (zones.size >= PrivacyZoneLimits.MAX_ZONES) return null
        val left = minOf(x1, x2).coerceIn(0f, 1f)
        val top = minOf(y1, y2).coerceIn(0f, 1f)
        val right = maxOf(x1, x2).coerceIn(0f, 1f)
        val bottom = maxOf(y1, y2).coerceIn(0f, 1f)
        if (right - left < PrivacyZoneLimits.MIN_SIZE || bottom - top < PrivacyZoneLimits.MIN_SIZE) return null
        return zones + PrivacyZone(idFactory(), nextName(zones), left, top, right - left, bottom - top, enabled = true)
    }

    private fun nextName(zones: List<PrivacyZone>): String {
        val used = zones.map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { "영역 $it" }.first { it !in used }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PrivacyZoneViewModel @Inject constructor(
    state: SavedStateHandle,
    private val repository: PrivacyZoneRepository,
    private val devices: DeviceRepository,
    private val resolver: StreamUrlResolver,
    private val factory: WhepSessionFactory,
    private val bus: UiMessageBus,
    engine: WebRtcEngine
) : ViewModel() {
    val eglContext = engine.eglContext
    val cameras = devices.cameras
    val pollInterval = CAMERA_POLL_MS

    private val _mac = MutableStateFlow(state.toRoute<PrivacyZoneRoute>().mac)
    val mac = _mac.asStateFlow()
    private val _saved = MutableStateFlow<List<PrivacyZone>>(emptyList())
    private val _draft = MutableStateFlow<List<PrivacyZone>>(emptyList())
    val draft = _draft.asStateFlow()
    val isDirty = combine(_saved, _draft) { a, b -> a != b }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _result = MutableStateFlow<String?>(null)
    val result = _result.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _session = MutableStateFlow<WhepSession?>(null)
    val session = _session.asStateFlow()
    private val started = MutableStateFlow(false)

    val streamState: StateFlow<StreamState> = _session
        .flatMapLatest { it?.state ?: flowOf(StreamState.Idle) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamState.Idle)
    val frameSize = _session.flatMapLatest { it?.frameSize ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            devices.cameras.collect { list ->
                if (_mac.value == null) list.items.firstOrNull()?.let { select(it.mac) }
            }
        }
        viewModelScope.launch {
            _mac.filterNotNull().distinctUntilChanged().collect { load(it) }
        }
        viewModelScope.launch {
            val url = combine(_mac, devices.cameras) { mac, list ->
                list.items.firstOrNull { it.mac == mac }?.takeIf { it.isOnline }?.let { resolver.resolve(it, StreamQuality.Main) }
            }.distinctUntilChanged()
            combine(url, started) { u, s -> u.takeIf { s } }.distinctUntilChanged().collect { target ->
                _session.value?.close()
                _session.value = target?.let(factory::open)
            }
        }
    }

    private suspend fun load(mac: String) {
        val zones = try {
            repository.current(mac)
        } catch (e: AppError) {
            bus.showError(e, "프라이버시 존 조회 실패")
            emptyList()
        }
        _saved.value = zones
        _draft.value = zones
        _result.value = null
    }

    fun setStarted(value: Boolean) {
        started.value = value
    }

    fun select(mac: String) {
        _mac.value = mac
    }

    fun addZone(x1: Float, y1: Float, x2: Float, y2: Float) {
        ZoneDraft.tryAdd(_draft.value, x1, y1, x2, y2) { UUID.randomUUID().toString() }?.let {
            _draft.value = it
            _result.value = null
        } ?: run {
            if (_draft.value.size >= PrivacyZoneLimits.MAX_ZONES) bus.show("프라이버시 존은 최대 ${PrivacyZoneLimits.MAX_ZONES}개까지 추가할 수 있습니다.")
            else bus.show("영역이 너무 작습니다. 더 크게 드래그해 주세요.")
        }
    }

    fun editZone(id: String, transform: (PrivacyZone) -> PrivacyZone) {
        _draft.value = _draft.value.map { if (it.id == id) transform(it) else it }
        _result.value = null
    }

    fun removeZone(id: String) {
        _draft.value = _draft.value.filterNot { it.id == id }
        _result.value = null
    }

    fun revert() {
        _draft.value = _saved.value
        _result.value = null
    }

    fun save() {
        val mac = _mac.value ?: return
        val online = devices.cameras.value.items.firstOrNull { it.mac == mac }?.isOnline == true
        viewModelScope.launch {
            _saving.value = true
            try {
                repository.save(mac, _draft.value, online)
                _saved.value = _draft.value
                _result.value = "저장되었습니다."
            } catch (e: AppError) {
                _result.value = e.message
            } finally {
                _saving.value = false
            }
        }
    }

    suspend fun poll(first: Boolean) {
        runCatching { devices.refresh(silent = !first) }
    }

    override fun onCleared() {
        _session.value?.close()
        super.onCleared()
    }
}
