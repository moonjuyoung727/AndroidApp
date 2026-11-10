package com.example.androidapplication.feature.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.EventQuery
import com.example.androidapplication.core.data.EventRepository
import com.example.androidapplication.core.data.EventStatus
import com.example.androidapplication.core.media.CaptureManager
import com.example.androidapplication.core.media.MediaEntity
import com.example.androidapplication.core.media.MediaType
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.navigation.EventDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import javax.inject.Inject

enum class EventPeriod(val key: String, val label: String, val daysBack: Int) {
    Today("today", "오늘", 0),
    Week("7d", "최근 7일", 6),
    Month("30d", "최근 30일", 29),
    TwoMonths("60d", "최근 60일", 59),
    Custom("custom", "날짜 선택", 0);

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Today
    }
}

data class EventFilters(
    val period: EventPeriod = EventPeriod.Today,
    val customDate: String? = null,
    val mac: String? = null,
    val type: String? = null,
    val status: String? = null,
    val search: String = ""
) {
    fun range(): Pair<String, String> =
        if (period == EventPeriod.Custom && customDate != null) customDate to customDate
        else KstTime.range(period.daysBack)
}

data class EventsUi(
    val items: List<EventItem> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val selected: Set<String> = emptySet(),
    val bulkBusy: Boolean = false
)

private const val PAGE_SIZE = 30
private const val BULK_PARALLEL = 4

/** 검색어는 ICD에 서버 파라미터가 없어 불러온 항목 안에서 거른다. 나머지 조건은 서버 쿼리로 보낸다. */
fun EventItem.matchesSearch(search: String): Boolean {
    val keyword = search.trim().lowercase()
    if (keyword.isEmpty()) return true
    return cameraName.lowercase().contains(keyword) || location.orEmpty().lowercase().contains(keyword) ||
        description.orEmpty().lowercase().contains(keyword)
}

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val state: SavedStateHandle,
    private val repository: EventRepository,
    private val bus: UiMessageBus,
    devices: DeviceRepository
) : ViewModel() {
    val cameras = devices.cameras
    val canUpdate get() = repository.canUpdate

    private val _filters = MutableStateFlow(
        EventFilters(
            period = EventPeriod.of(state["f_period"] ?: state.get<String>("period")),
            customDate = state["f_date"],
            mac = state["f_mac"],
            type = state["f_type"],
            status = state["f_status"] ?: state.get<String>("status"),
            search = state["f_search"] ?: ""
        )
    )
    val filters = _filters.asStateFlow()

    private val _ui = MutableStateFlow(EventsUi())
    val ui = _ui.asStateFlow()
    private var page = 0
    private var job: Job? = null

    init {
        reload()
    }

    fun update(transform: (EventFilters) -> EventFilters) {
        val previous = _filters.value
        val next = transform(previous)
        _filters.value = next
        state["f_period"] = next.period.key
        state["f_date"] = next.customDate
        state["f_mac"] = next.mac
        state["f_type"] = next.type
        state["f_status"] = next.status
        state["f_search"] = next.search
        // 검색어만 바뀌면 다시 받지 않는다
        if (previous.copy(search = "") != next.copy(search = "")) reload()
    }

    fun reset() = update { EventFilters() }

    private fun query(pageNo: Int, size: Int = PAGE_SIZE): EventQuery {
        val f = _filters.value
        val (start, end) = f.range()
        return EventQuery(pageNo, size, start, end, f.mac, f.type, f.status)
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(loading = true, selected = emptySet()) }
            try {
                val result = repository.list(query(1))
                page = 1
                _ui.update {
                    it.copy(items = result.items, total = result.total, loading = false, hasMore = result.items.size < result.total)
                }
            } catch (e: AppError) {
                bus.showError(e, "이벤트 조회 실패")
                _ui.update { it.copy(loading = false, hasMore = false) }
            }
        }
    }

    fun loadMore() {
        val current = _ui.value
        if (current.loading || !current.hasMore) return
        job = viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            try {
                val result = repository.list(query(page + 1))
                page += 1
                _ui.update {
                    val merged = (it.items + result.items).distinctBy(EventItem::id)
                    it.copy(items = merged, total = result.total, loading = false, hasMore = merged.size < result.total && result.items.isNotEmpty())
                }
            } catch (e: AppError) {
                bus.showError(e, "이벤트 조회 실패")
                _ui.update { it.copy(loading = false, hasMore = false) }
            }
        }
    }

    /** 상세에서 돌아왔을 때 불러온 범위를 유지한 채 다시 받는다. */
    fun refreshLoaded() {
        val loaded = _ui.value.items.size
        if (loaded == 0 || _ui.value.loading) return
        viewModelScope.launch {
            runCatching { repository.list(query(1, loaded.coerceAtLeast(PAGE_SIZE))) }.onSuccess { result ->
                _ui.update { it.copy(items = result.items, total = result.total, hasMore = result.items.size < result.total) }
            }
        }
    }

    fun toggle(id: String) = _ui.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun clearSelection() = _ui.update { it.copy(selected = emptySet()) }

    /** APP-F-034: 불러온 미확인 이벤트를 한 번에 선택한다. */
    fun selectUnconfirmed() = _ui.update { state ->
        state.copy(selected = state.items.filter { it.status == EventStatus.UNCONFIRMED }.map { it.id }.toSet())
    }

    /** 개별 PATCH를 호출하고 성공·실패 건수를 표시한다. */
    fun bulkUpdate(status: String) {
        val ids = _ui.value.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            _ui.update { it.copy(bulkBusy = true) }
            val limiter = Semaphore(BULK_PARALLEL)
            val results = coroutineScope {
                ids.map { id ->
                    async {
                        limiter.withPermit { id to runCatching { repository.update(id, status = status) }.isSuccess }
                    }
                }.awaitAll()
            }
            val succeeded = results.filter { it.second }.map { it.first }.toSet()
            _ui.update {
                it.copy(
                    bulkBusy = false,
                    selected = it.selected - succeeded,
                    items = it.items.map { e -> if (e.id in succeeded) e.copy(status = status) else e }
                )
            }
            bus.show("${EventStatus.label(status)} 처리: 성공 ${succeeded.size}건, 실패 ${ids.size - succeeded.size}건")
        }
    }
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    state: SavedStateHandle,
    private val repository: EventRepository,
    private val capture: CaptureManager,
    private val bus: UiMessageBus,
    val okHttp: OkHttpClient
) : ViewModel() {
    private val _event = MutableStateFlow(
        AppJson.decodeFromString<EventItem>(state.toRoute<EventDetailRoute>().eventJson)
    )
    val event = _event.asStateFlow()
    val canUpdate get() = repository.canUpdate

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _download = MutableStateFlow<DownloadState?>(null)
    val download = _download.asStateFlow()

    data class DownloadState(val type: MediaType, val progress: Float?)

    fun setStatus(status: String) = save(status = status)

    fun saveMemo(memo: String) {
        Validators.memo(memo)?.let { bus.show(it); return }
        save(memo = memo)
    }

    private fun save(status: String? = null, memo: String? = null) {
        viewModelScope.launch {
            _saving.value = true
            try {
                repository.update(_event.value.id, status = status, memo = memo)
                _event.update { it.copy(status = status ?: it.status, memo = memo ?: it.memo) }
                bus.show("저장되었습니다.")
            } catch (e: AppError) {
                bus.showError(e, "이벤트 상태 변경 실패")
            } finally {
                _saving.value = false
            }
        }
    }

    fun download(type: MediaType, onView: (MediaEntity) -> Unit) {
        val item = _event.value
        val url = if (type == MediaType.IMAGE) item.snapshotUrl else item.videoUrl
        if (url == null || _download.value != null) return
        viewModelScope.launch {
            _download.value = DownloadState(type, null)
            try {
                val saved = capture.downloadEventMedia(item, url, type) { _download.value = DownloadState(type, it) }
                bus.show("저장되었습니다", "보기") { onView(saved) }
            } catch (e: AppError) {
                bus.show(e.message)
            } finally {
                _download.value = null
            }
        }
    }
}
