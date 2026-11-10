package com.example.androidapplication.feature.events

import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.EventStatus
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.media.MediaType
import com.example.androidapplication.core.media.MediaUrls
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.feature.media.VideoPlayer
import com.example.androidapplication.navigation.EventDetailRoute
import com.example.androidapplication.navigation.MediaDetailRoute
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun eventStatusColor(status: String) = if (status == EventStatus.CONFIRMED) NeuSuccess else NeuWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(nav: NavHostController, vm: EventsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(filters.search.isNotEmpty()) }
    val listState = rememberLazyListState()
    var resumed by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        if (resumed) vm.refreshLoaded()
        resumed = true
        onPauseOrDispose { }
    }

    val visible = remember(ui.items, filters.search) { ui.items.filter { it.matchesSearch(filters.search) } }
    val selecting = ui.selected.isNotEmpty()
    val filterActive = filters.mac != null || filters.type != null || filters.status != null

    // 목록 끝에 가까워지면 다음 페이지를 받는다 (검색으로 짧아진 목록도 포함)
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, ui.hasMore, ui.loading, visible.size) {
        if (nearEnd && ui.hasMore && !ui.loading) vm.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = ScreenPadding)) {
            ChipRow(
                EventPeriod.entries.map { it to if (it == EventPeriod.Custom) (filters.customDate ?: it.label) else it.label },
                filters.period,
                { p -> if (p == EventPeriod.Custom) dateOpen = true else vm.update { it.copy(period = p, customDate = null) } }
            )
            VerticalGap(8)
            if (selecting) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DimIconButton(Icons.Default.Close, "선택 해제", vm::clearSelection)
                    Text("${ui.selected.size}건 선택", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextAction("확인 완료", { vm.bulkUpdate(EventStatus.CONFIRMED) }, enabled = vm.canUpdate && !ui.bulkBusy)
                    TextAction("미확인", { vm.bulkUpdate(EventStatus.UNCONFIRMED) }, enabled = vm.canUpdate && !ui.bulkBusy, color = NeuMuted)
                }
                if (!vm.canUpdate) NoticeBox("이벤트 상태 변경은 서버 연동 후 사용할 수 있습니다.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigNumber("${ui.total}", "건", Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
                    CircleIconButton(Icons.Default.Search, "검색", { searchOpen = !searchOpen }, size = 44.dp)
                    CircleIconButton(Icons.Default.Tune, "필터", { filterOpen = true }, size = 44.dp, badge = if (filterActive) 1 else 0)
                }
            }
            if (searchOpen && !selecting) {
                VerticalGap(10)
                SearchField(filters.search, { s -> vm.update { it.copy(search = s) } }, "카메라, 위치, 설명 검색")
            }
        }

        LazyColumn(Modifier.weight(1f), listState, contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 16.dp)) {
            if (visible.isEmpty() && !ui.loading) {
                item { EmptyState(Icons.AutoMirrored.Filled.EventNote, "표시할 이벤트가 없습니다.", "기간이나 필터를 바꿔 보세요.") }
            }
            items(visible, key = { it.id }) { event ->
                EventRow(
                    event, selecting, event.id in ui.selected,
                    onToggle = { vm.toggle(event.id) },
                    onOpen = { nav.navigate(EventDetailRoute(AppJson.encodeToString(EventItem.serializer(), event))) }
                )
                HorizontalDivider(color = NeuLine)
            }
            if (ui.loading) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeuAccent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }

    if (filterOpen) CleanSheet(onDismiss = { filterOpen = false }, title = "필터") {
        ListGroup {
            SettingSelect(
                "카메라", null,
                listOf<Pair<String?, String>>(null to "전체") + cameras.items.map { it.mac to it.name.ifBlank { it.deviceId } },
                filters.mac, { mac -> vm.update { it.copy(mac = mac) } }
            )
            SettingSelect(
                "종류", null,
                listOf<Pair<String?, String>>(null to "전체") + EventTypes.labels.map { (k, v) -> k to v },
                filters.type, { t -> vm.update { it.copy(type = t) } }
            )
            SettingSelect(
                "처리 여부", null,
                listOf<Pair<String?, String>>(null to "전체", EventStatus.UNCONFIRMED to "미확인", EventStatus.CONFIRMED to "확인 완료"),
                filters.status, { s -> vm.update { it.copy(status = s) } }
            )
        }
        VerticalGap(14)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuOutlineButton("미확인 모두 선택", { filterOpen = false; vm.selectUnconfirmed() }, Modifier.weight(1f))
            NeuOutlineButton("필터 초기화", { filterOpen = false; vm.reset() }, Modifier.weight(1f))
        }
    }

    if (dateOpen) {
        val todayMs = remember { KstTime.today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val picker = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMs // 미래 날짜 불가
            }
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        val date: LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.update { it.copy(period = EventPeriod.Custom, customDate = date.toString()) }
                    }
                    dateOpen = false
                }) { Text("선택") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("취소", color = NeuMuted) } }
        ) { DatePicker(picker) }
    }
}

/** 길게 누르면 다중 선택이 시작된다. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRow(event: EventItem, selecting: Boolean, selected: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggle() else onOpen() },
                onLongClick = onToggle,
                onLongClickLabel = "선택"
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selecting) {
            Checkbox(
                selected, { onToggle() }, Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(checkedColor = NeuAccent, checkmarkColor = NeuBg, uncheckedColor = NeuMuted)
            )
        } else {
            Box(Modifier.size(8.dp).background(eventColor(event.type), CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(EventTypes.label(event.type), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${event.cameraName} · ${KstTime.format(event.occurredAt)}", color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusPill(EventStatus.label(event.status), eventStatusColor(event.status))
    }
}

@AndroidxOptIn(UnstableApi::class)
@Composable
fun EventDetailScreen(nav: NavHostController, vm: EventDetailViewModel = hiltViewModel()) {
    val event by vm.event.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var memo by rememberSaveable { mutableStateOf(event.memo.orEmpty()) }
    var viewerTab by rememberSaveable { mutableStateOf<String?>(null) }
    val snapshot = remember(event.snapshotUrl) { MediaUrls.resolve(event.snapshotUrl) }
    val video = remember(event.videoUrl) { MediaUrls.resolve(event.videoUrl) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (snapshot != null) {
            AsyncImage(
                snapshot, "이벤트 스냅샷",
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(NeuCardShape).background(Color.Black).clickable { viewerTab = "snapshot" },
                contentScale = ContentScale.Fit
            )
        }
        Column {
            Text(EventTypes.label(event.type), style = MaterialTheme.typography.headlineSmall)
            VerticalGap(6)
            StatusPill(EventStatus.label(event.status), eventStatusColor(event.status))
        }
        ListGroup {
            ListRow("시간", value = KstTime.format(event.occurredAt))
            ListRow("카메라", value = "${event.cameraName}${event.location?.let { " ($it)" } ?: ""}")
            // APP-F-032 감지 정보
            event.soundLevelDb?.let { ListRow("소리 크기", value = "%.1f dB".format(it)) }
            event.zoneId?.let { ListRow("감지 영역", value = it) }
            ListRow("설명", subtitle = event.description ?: "${EventTypes.label(event.type)} 이벤트가 발생했습니다.", divider = false)
        }
        if (video != null || snapshot != null) {
            NeuOutlineButton(if (video != null) "영상 보기" else "스냅샷 보기", { viewerTab = if (video != null) "video" else "snapshot" }, Modifier.fillMaxWidth(), icon = Icons.Default.PlayArrow)
        }
        ChipRow(
            listOf(EventStatus.UNCONFIRMED to "미확인", EventStatus.CONFIRMED to "확인 완료"),
            event.status, vm::setStatus, enabled = vm.canUpdate && !saving
        )
        NeuTextField(
            memo, { memo = it.take(Validators.EVENT_MEMO_MAX) }, "메모",
            singleLine = false, minLines = 3, enabled = vm.canUpdate,
            counter = "${memo.length}/${Validators.EVENT_MEMO_MAX}"
        )
        if (vm.canUpdate) {
            PrimaryButton("메모 저장", { vm.saveMemo(memo) }, Modifier.fillMaxWidth(), enabled = !saving && memo != event.memo.orEmpty())
        } else {
            NoticeBox("상태 변경과 메모는 서버 연동 후 사용할 수 있습니다.")
        }
        VerticalGap(8)
    }

    viewerTab?.let { tab ->
        Dialog(onDismissRequest = { viewerTab = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            EventMediaViewer(event, tab, vm, onTab = { viewerTab = it }, onClose = { viewerTab = null }, onViewSaved = { nav.navigate(MediaDetailRoute(it)) })
        }
    }
}

@AndroidxOptIn(UnstableApi::class)
@Composable
private fun EventMediaViewer(
    event: EventItem,
    tab: String,
    vm: EventDetailViewModel,
    onTab: (String) -> Unit,
    onClose: () -> Unit,
    onViewSaved: (Long) -> Unit
) {
    val download by vm.download.collectAsStateWithLifecycle()
    val snapshot = remember(event.snapshotUrl) { MediaUrls.resolve(event.snapshotUrl) }
    val video = remember(event.videoUrl) { MediaUrls.resolve(event.videoUrl) }
    val type = if (tab == "snapshot") MediaType.IMAGE else MediaType.VIDEO

    Surface(Modifier.fillMaxSize(), color = NeuBg) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(ScreenPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipRow(listOf("snapshot" to "스냅샷", "video" to "이벤트 영상"), tab, onTab, Modifier.weight(1f))
                CircleIconButton(Icons.Default.Close, "닫기", onClose, size = 44.dp)
            }
            VerticalGap(12)
            Box(Modifier.weight(1f).fillMaxWidth().clip(NeuCardShape).background(Color.Black), contentAlignment = Alignment.Center) {
                when {
                    tab == "snapshot" && snapshot != null -> AsyncImage(snapshot, "이벤트 스냅샷", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    tab == "video" && video != null -> {
                        val factory = remember { OkHttpDataSource.Factory(vm.okHttp) }
                        VideoPlayer(video, factory, Modifier.fillMaxSize())
                    }
                    else -> Text("미디어가 없습니다.", color = NeuMuted)
                }
            }
            VerticalGap(10)
            Text("${event.cameraName} · ${KstTime.format(event.occurredAt)}", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
            VerticalGap(12)
            val busy = download
            if (busy != null) {
                if (busy.progress != null) LinearProgressIndicator({ busy.progress }, Modifier.fillMaxWidth(), color = NeuAccent, trackColor = NeuSurfaceStrong)
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = NeuAccent, trackColor = NeuSurfaceStrong)
                VerticalGap(10)
            }
            val available = if (type == MediaType.IMAGE) snapshot != null else video != null
            PrimaryButton(if (busy != null) "다운로드 중..." else "다운로드", { vm.download(type) { onViewSaved(it.id) } }, Modifier.fillMaxWidth(), enabled = available && busy == null)
        }
    }
}
