package com.example.androidapplication.feature.monitoring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.DeviceStatus
import com.example.androidapplication.core.data.PrivacyZone
import com.example.androidapplication.core.data.PrivacyZoneRepository
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.designsystem.ZoomView
import com.example.androidapplication.core.media.CaptureManager
import com.example.androidapplication.core.media.MediaLibrary
import com.example.androidapplication.core.media.RecordingHandle
import com.example.androidapplication.core.media.ZONES_UNAVAILABLE
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.vpn.ClientVpnState
import com.example.androidapplication.core.vpn.VpnMonitor
import com.example.androidapplication.core.webrtc.FrameSize
import com.example.androidapplication.core.webrtc.StreamQuality
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.StreamUrlResolver
import com.example.androidapplication.core.webrtc.WebRtcEngine
import com.example.androidapplication.core.webrtc.WhepSession
import com.example.androidapplication.core.webrtc.WhepSessionFactory
import com.example.androidapplication.core.webrtc.keepStreaming
import com.example.androidapplication.navigation.MonitoringRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

data class RecordingUi(val active: Boolean = false, val elapsedMs: Long = 0, val sizeBytes: Long = 0)

private const val STORAGE_CHECK_EVERY_TICKS = 10

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonitoringViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val zoneRepository: PrivacyZoneRepository,
    private val sessions: WhepSessionFactory,
    private val resolver: StreamUrlResolver,
    private val capture: CaptureManager,
    private val library: MediaLibrary,
    private val bus: UiMessageBus,
    private val vpn: VpnMonitor,
    private val state: SavedStateHandle,
    engine: WebRtcEngine,
    settings: SettingsRepository,
    @param:Named("app") private val appScope: CoroutineScope
) : ViewModel() {
    val mac: String = state.toRoute<MonitoringRoute>().mac
    val eglContext = engine.eglContext
    val general = settings.general
    val canRename get() = devices.canUpdate
    val cameraList = devices.cameras

    val camera: StateFlow<Camera?> = devices.cameras
        .map { list -> list.items.firstOrNull { it.mac == mac } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, devices.cameras.value.items.firstOrNull { it.mac == mac })

    private val _session = MutableStateFlow<WhepSession?>(null)
    val session = _session.asStateFlow()
    val streamState: StateFlow<StreamState> = _session
        .flatMapLatest { it?.state ?: flowOf(StreamState.Idle) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StreamState.Idle)
    val frameSize: StateFlow<FrameSize?> = _session
        .flatMapLatest { it?.frameSize ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _reconnecting = MutableStateFlow(false)
    val reconnecting = _reconnecting.asStateFlow()
    private val _waitingVpn = MutableStateFlow(false)
    val waitingVpn = _waitingVpn.asStateFlow()
    private val _streamFailures = MutableStateFlow(0)
    /** 연속 실패 횟수. [isStreamFailedOut]이면 '실패'로 표시한다. */
    val streamFailures = _streamFailures.asStateFlow()

    private val _quality = MutableStateFlow(StreamQuality.Main)
    val quality = _quality.asStateFlow()
    private val _muted = MutableStateFlow(true)
    val muted = _muted.asStateFlow()
    private val _volume = MutableStateFlow(0.3f)
    val volume = _volume.asStateFlow()

    private val _zoom = MutableStateFlow(
        state.get<String>(KEY_ZOOM)?.let { runCatching { AppJson.decodeFromString<ZoomView>(it) }.getOrNull() } ?: ZoomView()
    )
    val zoom = _zoom.asStateFlow()

    private val _zones = MutableStateFlow<List<PrivacyZone>>(emptyList())
    val zones = _zones.asStateFlow()
    private val _zonesAvailable = MutableStateFlow(true)
    val zonesAvailable = _zonesAvailable.asStateFlow()

    private val _status = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus = _status.asStateFlow()

    private val _recording = MutableStateFlow(RecordingUi())
    val recording = _recording.asStateFlow()
    private val _shutter = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shutter = _shutter.asSharedFlow()

    private val _fullscreen = MutableStateFlow(false)
    val fullscreen = _fullscreen.asStateFlow()

    private val started = MutableStateFlow(false)
    private val reloadKey = MutableStateFlow(0)
    private var handle: RecordingHandle? = null
    private var ticker: Job? = null
    private var starting = false

    init {
        viewModelScope.launch {
            val desired = combine(camera, _quality) { cam, q -> if (cam?.isOnline == true) resolver.resolve(cam, q) else null }
                .distinctUntilChanged()
            combine(desired, started, reloadKey) { url, isStarted, key -> Triple(url, isStarted, key) }
                .distinctUntilChanged()
                .collectLatest { (url, isStarted, _) ->
                    withContext(NonCancellable) {
                        closeSession(if (isStarted) "영상 연결이 종료되어 녹화를 저장했습니다." else "화면을 벗어나 녹화를 저장했습니다.")
                    }
                    _reconnecting.value = false
                    _waitingVpn.value = false
                    _streamFailures.value = 0
                    if (url != null && isStarted) {
                        keepStreaming(
                            open = {
                                sessions.open(url).also {
                                    it.setAudio(_muted.value, _volume.value.toDouble())
                                    _session.value = it
                                }.state
                            },
                            release = { withContext(NonCancellable) { closeSession("영상 연결이 끊겨 녹화를 저장했습니다.") } },
                            onRetry = { _reconnecting.value = true },
                            network = vpn.usable,
                            onWaitingNetwork = { _waitingVpn.value = it },
                            onFailures = { _streamFailures.value = it }
                        )
                    }
                }
        }
        viewModelScope.launch {
            zoneRepository.observe(mac)
                .catch { _zonesAvailable.value = false }
                .collect { list ->
                    _zones.value = list
                    handle?.updateZones(list)
                }
        }
        viewModelScope.launch {
            _status.value = runCatching { devices.status(mac) }.getOrNull()
        }
        // VPN이 끊기면 그때까지의 내용을 저장한다 (영상 실패는 재연결 전에 저장한다)
        viewModelScope.launch {
            vpn.ui.map { it.status == ClientVpnState.Error }.distinctUntilChanged().collect { lost ->
                if (lost) stopRecordingWith("VPN 연결이 끊겨 녹화를 저장했습니다.")
            }
        }
    }

    fun setStarted(value: Boolean) {
        started.value = value
    }

    suspend fun poll(first: Boolean): Boolean = try {
        devices.refresh(silent = !first)
    } catch (e: AppError) {
        bus.showError(e, "카메라 목록 조회에 실패했습니다.")
        false
    }

    fun refreshStream() = reloadKey.update { it + 1 }

    fun selectQuality(value: StreamQuality) {
        if (handle == null) _quality.value = value else bus.show("녹화 중에는 화질을 변경할 수 없습니다.")
    }

    fun qualities(cam: Camera) = resolver.qualities(cam)

    fun toggleMute() {
        _muted.value = !_muted.value
        applyAudio()
    }

    fun setVolume(value: Float) {
        _volume.value = value
        if (value > 0f && _muted.value) _muted.value = false
        applyAudio()
    }

    private fun applyAudio() {
        _session.value?.setAudio(_muted.value, _volume.value.toDouble())
    }

    fun setZoom(view: ZoomView) {
        _zoom.value = view
        state[KEY_ZOOM] = AppJson.encodeToString(ZoomView.serializer(), view)
    }

    fun setFullscreen(value: Boolean) {
        _fullscreen.value = value
    }

    fun rename(name: String) {
        viewModelScope.launch {
            try {
                devices.update(mac, name = name.trim())
                bus.show("이름이 변경되었습니다.")
            } catch (e: AppError) {
                bus.showError(e, "카메라 정보 수정에 실패했습니다.")
            }
        }
    }

    /** 저장은 connected 상태의 메인 스트림에서만 가능하다. */
    fun saveBlockReason(stream: StreamState, q: StreamQuality, available: Boolean): String? = when {
        stream != StreamState.Connected -> "영상이 연결되면 저장할 수 있습니다."
        q != StreamQuality.Main -> "고화질(메인 스트림)에서만 저장할 수 있습니다."
        !available -> ZONES_UNAVAILABLE
        else -> null
    }

    fun snapshot(onView: (Long) -> Unit) {
        val session = _session.value ?: return
        val cam = camera.value ?: return
        viewModelScope.launch {
            _shutter.tryEmit(Unit)
            try {
                val saved = capture.snapshot(session, cam)
                bus.show("저장되었습니다", "보기") { onView(saved.id) }
            } catch (e: AppError) {
                bus.show(e.message)
            }
        }
    }

    fun toggleRecording() {
        if (handle != null) {
            viewModelScope.launch { stopRecordingWith("녹화가 저장되었습니다.") }
            return
        }
        val session = _session.value ?: return
        val cam = camera.value ?: return
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                val h = capture.startRecording(session, cam) { error ->
                    viewModelScope.launch { stopRecordingWith("녹화 중 오류가 발생해 지금까지의 내용을 저장했습니다.") }
                    android.util.Log.e("Monitoring", "recording failed", error)
                }
                h.updateZones(_zones.value)
                handle = h
                _recording.value = RecordingUi(active = true)
                startTicker(h)
            } catch (e: AppError) {
                bus.show(e.message)
            } finally {
                starting = false
            }
        }
    }

    private fun startTicker(h: RecordingHandle) {
        ticker = viewModelScope.launch {
            var ticks = 0
            while (true) {
                delay(1_000)
                _recording.value = RecordingUi(true, System.currentTimeMillis() - h.startedAtMs, h.writtenBytes)
                if (++ticks % STORAGE_CHECK_EVERY_TICKS == 0) {
                    library.recordingStopReason(h.currentSegmentBytes)?.let {
                        stopRecordingWith(it)
                        return@launch
                    }
                }
            }
        }
    }

    /** 앱 스코프에서 끝까지 진행해 화면이 사라져도 재생 가능한 MP4가 남는다. */
    private fun finishRecording(message: String): Job? {
        val h = handle ?: return null
        handle = null
        ticker?.cancel()
        ticker = null
        _recording.value = RecordingUi()
        return appScope.launch {
            val saved = h.stop()
            bus.show(if (saved.isEmpty()) "저장할 영상이 없어 녹화를 취소했습니다." else message)
        }
    }

    private suspend fun stopRecordingWith(message: String) {
        finishRecording(message)?.join()
    }

    private suspend fun closeSession(recordingMessage: String) {
        finishRecording(recordingMessage)?.join()
        _session.value?.close()
        _session.value = null
    }

    override fun onCleared() {
        finishRecording("화면을 벗어나 녹화를 저장했습니다.")
        _session.value?.close()
        _session.value = null
        super.onCleared()
    }

    private fun MutableStateFlow<Int>.update(transform: (Int) -> Int) {
        value = transform(value)
    }

    private companion object {
        const val KEY_ZOOM = "zoom_view"
    }
}
