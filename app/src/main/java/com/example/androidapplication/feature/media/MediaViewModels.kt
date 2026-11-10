package com.example.androidapplication.feature.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.media.MediaEntity
import com.example.androidapplication.core.media.MediaLibrary
import com.example.androidapplication.core.media.MediaSource
import com.example.androidapplication.core.media.MediaType
import com.example.androidapplication.core.session.SessionManager
import com.example.androidapplication.navigation.MediaDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class MediaFilters(
    val type: MediaType? = null,
    val mac: String? = null,
    val daysBack: Int? = null,
    /** null: 전체, "__manual": 직접 저장, 그 외: 이벤트 종류 */
    val origin: String? = null,
    val newestFirst: Boolean = true
)

const val ORIGIN_MANUAL = "__manual"

fun applyMediaFilters(items: List<MediaEntity>, filters: MediaFilters, nowMs: Long): List<MediaEntity> {
    val cutoff = filters.daysBack?.let {
        KstTime.today(Instant.ofEpochMilli(nowMs)).minusDays(it.toLong()).atStartOfDay(KstTime.ZONE).toInstant().toEpochMilli()
    }
    val filtered = items.filter { item ->
        (filters.type == null || item.type == filters.type) &&
            (filters.mac == null || item.mac == filters.mac) &&
            (cutoff == null || item.capturedAtUtc >= cutoff) &&
            when (filters.origin) {
                null -> true
                ORIGIN_MANUAL -> item.source != MediaSource.EVENT_DOWNLOAD
                else -> item.eventType == filters.origin
            }
    }
    return if (filters.newestFirst) filtered.sortedByDescending { it.capturedAtUtc } else filtered.sortedBy { it.capturedAtUtc }
}

@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    private val library: MediaLibrary,
    sessions: SessionManager,
    private val bus: UiMessageBus
) : ViewModel() {
    private val owner = sessions.session?.userId.orEmpty()
    private val _filters = MutableStateFlow(MediaFilters())
    val filters = _filters.asStateFlow()
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected = _selected.asStateFlow()

    // ownerUserId로 걸러 로그인한 계정의 파일만 보여 준다
    val allItems = (if (owner.isEmpty()) emptyFlow() else library.observe(owner))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val items = combine(allItems, _filters) { list, f -> applyMediaFilters(list, f, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { if (owner.isNotEmpty()) library.reconcile(owner) }
    }

    fun setFilters(transform: (MediaFilters) -> MediaFilters) {
        _filters.value = transform(_filters.value)
    }

    fun toggle(id: Long) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    private fun selectedItems() = allItems.value.filter { it.id in _selected.value }

    fun deleteSelected() {
        val ids = _selected.value
        viewModelScope.launch {
            library.delete(ids, owner)
            _selected.value = emptySet()
            bus.show("${ids.size}개 파일을 삭제했습니다.")
        }
    }

    fun shareIntent() = selectedItems().takeIf { it.isNotEmpty() }?.let(library::shareIntent)

    fun exportSelected() {
        val items = selectedItems()
        viewModelScope.launch {
            val count = library.exportToGallery(items)
            _selected.value = emptySet()
            bus.show("갤러리로 내보내기: 성공 ${count}개, 실패 ${items.size - count}개")
        }
    }
}

@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    state: SavedStateHandle,
    private val library: MediaLibrary,
    sessions: SessionManager,
    private val bus: UiMessageBus
) : ViewModel() {
    private val owner = sessions.session?.userId.orEmpty()
    private val id = state.toRoute<MediaDetailRoute>().id
    val item = library.observeOne(id, owner).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shareIntent(entity: MediaEntity) = library.shareIntent(listOf(entity))

    fun export(entity: MediaEntity) {
        viewModelScope.launch {
            val ok = library.exportToGallery(listOf(entity)) == 1
            bus.show(if (ok) "갤러리로 내보냈습니다." else "갤러리로 내보내지 못했습니다.")
        }
    }

    fun delete(entity: MediaEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            library.delete(listOf(entity.id), owner)
            bus.show("파일을 삭제했습니다.")
            onDone()
        }
    }
}
