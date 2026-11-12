package com.example.androidapplication.feature.dashboard

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.EventQuery
import com.example.androidapplication.core.data.EventRepository
import com.example.androidapplication.core.data.EventStatus
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.data.VpnDevice
import com.example.androidapplication.core.data.VpnRepository
import com.example.androidapplication.core.data.VpnStatusText
import com.example.androidapplication.core.vpn.VpnMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatsPeriod(val label: String, val daysBack: Int) {
    Today("오늘", 0), Week("7일", 6), Month("30일", 29)
}

data class DashboardEvents(
    val today: Int = 0,
    val unconfirmed: Int = 0,
    val typeCounts: Map<String, Int> = emptyMap(),
    val recent: List<EventItem> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val devices: DeviceRepository,
    private val events: EventRepository,
    private val vpn: VpnRepository,
    private val bus: UiMessageBus,
    monitor: VpnMonitor,
    settings: SettingsRepository
) : ViewModel() {
    val cameras = devices.cameras
    val vpnDevices = vpn.devices
    val client = monitor.ui
    val general = settings.general

    private val _events = MutableStateFlow(DashboardEvents())
    val eventState = _events.asStateFlow()
    private val period = MutableStateFlow(StatsPeriod.Today)
    val selectedPeriod = period.asStateFlow()
    /** 보드 목록. 성공하면 10초, 실패하면 15초 뒤 다시. */
    suspend fun poll(first: Boolean): Boolean = try {
        devices.refresh(silent = !first).also { vpn.refresh(silent = true) }
    } catch (e: AppError) {
        bus.showError(e, "카메라 목록 조회에 실패했습니다.")
        false
    }

    fun choosePeriod(value: StatsPeriod) {
        period.value = value
        viewModelScope.launch { loadEvents() }
    }

    /**
     * 최근 이벤트·통계. 보드 목록과 따로 성공하면 30초, 실패하면 15초 뒤 다시 받는다.
     * 대시보드 요약 API가 없어 GET /events의 total로 계산한다.
     */
    suspend fun pollEvents(first: Boolean): Boolean = loadEvents()

    private suspend fun loadEvents(): Boolean {
        return try {
            val (start, end) = KstTime.range(period.value.daysBack)
            val (todayStart, todayEnd) = KstTime.range(0)
            coroutineScope {
                val recent = async { events.list(EventQuery(size = 12)).items }
                val today = async { events.list(EventQuery(size = 1, startDate = todayStart, endDate = todayEnd)).total }
                val unconfirmed = async { events.list(EventQuery(size = 1, status = EventStatus.UNCONFIRMED)).total }
                val counts = EventTypes.labels.keys.map { type ->
                    async { type to events.list(EventQuery(size = 1, startDate = start, endDate = end, eventType = type)).total }
                }
                _events.update {
                    DashboardEvents(today.await(), unconfirmed.await(), counts.awaitAll().toMap(), recent.await())
                }
            }
            true
        } catch (_: AppError) {
            // 자동 재조회의 실패는 알리지 않는다. VPN 배너는 그대로 동작한다
            false
        }
    }
}

/** 문제 기기 우선 정렬 (오류 → 끊김 → 정상). */
fun sortVpnIssuesFirst(devices: List<VpnDevice>): List<VpnDevice> = devices.sortedBy {
    when (it.vpnStatus) {
        VpnStatusText.ERROR -> 0
        VpnStatusText.DISCONNECTED -> 1
        else -> 2
    }
}
