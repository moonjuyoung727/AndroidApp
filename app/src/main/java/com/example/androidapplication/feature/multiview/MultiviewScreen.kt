package com.example.androidapplication.feature.multiview

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.androidapplication.core.common.CAMERA_POLL_MS
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.VpnStatusText
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.media.MediaUrls
import com.example.androidapplication.core.webrtc.FrameSize
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.VideoLayer
import com.example.androidapplication.core.webrtc.WhepSession
import com.example.androidapplication.core.webrtc.isStreamFailedOut
import com.example.androidapplication.core.webrtc.keepStreaming
import com.example.androidapplication.navigation.LocalShellChrome
import com.example.androidapplication.navigation.MonitoringRoute
import com.example.androidapplication.navigation.rememberKstClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val STATUS_FILTERS = listOf("all" to "전체", "online" to "온라인", "offline" to "오프라인")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiviewScreen(nav: NavHostController, initialStatus: String?, vm: MultiviewViewModel = hiltViewModel()) {
    val state by vm.cameras.collectAsStateWithLifecycle()
    val general by vm.general.collectAsStateWithLifecycle()
    var chosenChannels by rememberSaveable { mutableStateOf<Int?>(null) }
    var keyword by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(initialStatus ?: "all") }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    PollWhileStarted(CAMERA_POLL_MS, repeat = general.autoRefresh, tick = vm::poll)

    LaunchedEffect(highlight) {
        if (highlight != null) {
            delay(1_600)
            highlight = null
        }
    }

    val chrome = LocalShellChrome.current
    if (fullscreen) {
        ImmersiveLandscape()
        DisposableEffect(Unit) {
            chrome.hidden = true
            onDispose { chrome.hidden = false }
        }
        BackHandler { fullscreen = false }
    }

    val channels = chosenChannels ?: general.defaultChannel
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val (cols, rows) = channelLayout(channels, landscape)
    val perPage = cols * rows

    val all = if (general.showOfflineBoards) state.items else state.items.filter(Camera::isOnline)
    // CH 번호는 필터와 상관없이 전체 목록 순서로 고정한다
    val channelNo = remember(state.items) { state.items.withIndex().associate { (i, c) -> c.mac to i + 1 } }
    val filtered = remember(all, keyword, status) { all.filter { matchesCamera(it, keyword, status) } }
    val pageCount = ((filtered.size + perPage - 1) / perPage).coerceAtLeast(1)
    val pager = rememberPagerState(pageCount = { pageCount })

    // 페이지 수가 줄면 마지막 페이지로 보정한다
    LaunchedEffect(pageCount) {
        if (pager.currentPage >= pageCount) pager.scrollToPage(pageCount - 1)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycle.value.isAtLeast(Lifecycle.State.STARTED)

    Column(Modifier.fillMaxSize().then(if (fullscreen) Modifier.background(Color.Black) else Modifier.padding(horizontal = ScreenPadding))) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(listOf(2 to "2채널", 4 to "4채널", 6 to "6채널"), channels, { chosenChannels = it }, Modifier.weight(1f))
                CircleIconButton(Icons.Default.Search, "채널 목록", { sheetOpen = true }, size = 44.dp)
                CircleIconButton(Icons.Default.Fullscreen, "전체 화면", { fullscreen = true }, size = 44.dp)
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0, pageSpacing = 12.dp) { page ->
            val items = filtered.drop(page * perPage).take(perPage)
            // 현재 페이지의 카드만 스트림을 연결하고 페이지를 벗어나거나 ON_STOP이면 끊는다
            val active = started && pager.settledPage == page
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(rows) { r ->
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(cols) { c ->
                            val camera = items.getOrNull(r * cols + c)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (camera != null) {
                                    ChannelCard(camera, channelNo[camera.mac] ?: 0, active, highlight == camera.mac, vm) { nav.navigate(MonitoringRoute(camera.mac)) }
                                } else {
                                    Box(Modifier.fillMaxSize().surfaceCard(NeuFieldShape), contentAlignment = Alignment.Center) {
                                        Text("빈 채널", color = NeuMuted.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!fullscreen) {
            val online = all.count(Camera::isOnline)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("전체 ${all.size} · 온라인 $online · 오프라인 ${all.size - online}", Modifier.weight(1f), color = NeuMuted, style = MaterialTheme.typography.labelMedium)
                Text("${pager.currentPage + 1} / $pageCount", color = NeuMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (sheetOpen) ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = NeuBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 20.dp)) {
            SearchField(keyword, { keyword = it }, "이름, 위치, MAC 검색")
            VerticalGap(8)
            ChipRow(STATUS_FILTERS, status, { status = it })
            VerticalGap(4)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(filtered, key = { it.mac }) { camera ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            sheetOpen = false
                            highlight = camera.mac
                            // 시트가 닫혀도 페이지 이동이 끝나도록 화면 스코프에서 실행한다
                            scope.launch { pager.animateScrollToPage(filtered.indexOf(camera) / perPage) }
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("${channelNo[camera.mac] ?: 0}", color = NeuMuted, style = MaterialTheme.typography.labelLarge)
                        Column(Modifier.weight(1f)) {
                            Text(camera.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text("${camera.location} · ${Mac.format(camera.mac)}", color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        StatusPill(if (camera.isOnline) "온라인" else "오프라인", if (camera.isOnline) NeuSuccess else NeuMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(camera: Camera, channel: Int, active: Boolean, highlighted: Boolean, vm: MultiviewViewModel, onOpen: () -> Unit) {
    val url = remember(camera.mac, camera.webrtcUrlSub, camera.isOnline) { if (camera.isOnline) vm.subStreamUrl(camera) else null }
    var session by remember { mutableStateOf<WhepSession?>(null) }
    var reconnecting by remember { mutableStateOf(false) }
    var waitingVpn by remember { mutableStateOf(false) }
    var failures by remember { mutableIntStateOf(0) }
    val target = url.takeIf { active }
    LaunchedEffect(target) {
        reconnecting = false
        waitingVpn = false
        failures = 0
        if (target == null) return@LaunchedEffect
        val release = {
            session?.close()
            session = null
        }
        try {
            keepStreaming(
                open = { vm.sessions.open(target).also { session = it }.state },
                release = release,
                onRetry = { reconnecting = true },
                network = vm.vpnUsable,
                onWaitingNetwork = { waitingVpn = it },
                onFailures = { failures = it }
            )
        } finally {
            release()
        }
    }
    val idleState = remember { MutableStateFlow(StreamState.Idle) }
    val idleSize = remember { MutableStateFlow<FrameSize?>(null) }
    val stream by (session?.state ?: idleState).collectAsStateWithLifecycle()
    val size by (session?.frameSize ?: idleSize).collectAsStateWithLifecycle()
    val zones by remember(camera.mac) { vm.zones(camera.mac) }.collectAsStateWithLifecycle(emptyList())
    var detailOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .clip(NeuFieldShape)
            .background(Color.Black)
            .then(if (highlighted) Modifier.border(2.dp, NeuAccent, NeuFieldShape) else Modifier)
            .clickable(onClick = onOpen)
    ) {
        when {
            !camera.isOnline -> Text("오프라인", Modifier.align(Alignment.Center), color = NeuMuted, style = MaterialTheme.typography.labelMedium)
            url == null -> {
                val thumbnail = remember(camera.thumbnailUrl) { MediaUrls.resolve(camera.thumbnailUrl) }
                if (thumbnail != null) AsyncImage(thumbnail, "${camera.name} 썸네일", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text(
                    "저화질 스트림 없음",
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.55f), NeuFieldShape).padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White, style = MaterialTheme.typography.labelMedium
                )
            }
            session != null -> {
                val ratio = size?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (16f / 9f)
                Box(Modifier.aspectRatio(ratio).align(Alignment.Center).clipToBounds()) {
                    VideoLayer(session, vm.eglContext, zones, Modifier.fillMaxSize())
                }
            }
        }

        val live = camera.isOnline && stream == StreamState.Connected
        StatusPill(
            when {
                !camera.isOnline -> "OFFLINE · CH$channel"
                live -> "LIVE · CH$channel"
                else -> "CH$channel"
            },
            if (live) NeuDanger else NeuMuted,
            Modifier.align(Alignment.TopStart).padding(10.dp)
        )
        DimIconButton(Icons.Default.Tune, "${camera.name} 상세", { detailOpen = true }, Modifier.align(Alignment.TopEnd), tint = Color.White)
        Text(
            camera.name,
            Modifier.align(Alignment.BottomStart).padding(10.dp),
            color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        val streamMessage = when {
            !camera.isOnline || stream == StreamState.Connected -> null
            waitingVpn -> "VPN 연결 확인 필요"
            isStreamFailedOut(failures) -> "연결 실패 · 재시도 중"
            reconnecting || stream == StreamState.Failed -> "재연결 중..."
            stream == StreamState.Connecting -> "연결 중..."
            else -> null
        }
        if (streamMessage != null) {
            Text(streamMessage, Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        }
    }

    if (detailOpen) CleanSheet(onDismiss = { detailOpen = false }, title = camera.name) {
        InfoRow("현재 시각", rememberKstClock())
        InfoRow("화질", "SD")
        InfoRow("위치", camera.location.ifBlank { "-" })
        InfoRow("VPN", VpnStatusText.label(camera.vpnStatus), valueColor = vpnStatusColor(camera.vpnStatus))
        InfoRow("보드 ID", camera.deviceId)
        InfoRow("MAC", Mac.format(camera.mac))
        InfoRow("VPN IP", camera.vpnIp ?: "-")
        InfoRow("펌웨어", camera.firmware ?: "-")
        InfoRow("최근 이벤트", KstTime.format(camera.latestEventTime))
        InfoRow("마지막 수신", KstTime.formatLastSeen(camera.lastSeen))
    }
}
