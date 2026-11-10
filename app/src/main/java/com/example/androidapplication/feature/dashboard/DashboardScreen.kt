package com.example.androidapplication.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.androidapplication.core.common.DASHBOARD_POLL_MS
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.EventStatus
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.data.VpnStatusText
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.media.MediaUrls
import com.example.androidapplication.navigation.*

private fun NavHostController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
}

@Composable
fun DashboardScreen(nav: NavHostController, vm: DashboardViewModel = hiltViewModel()) {
    val cameraState by vm.cameras.collectAsStateWithLifecycle()
    val events by vm.eventState.collectAsStateWithLifecycle()
    val vpnState by vm.vpnDevices.collectAsStateWithLifecycle()
    val client by vm.client.collectAsStateWithLifecycle()
    val general by vm.general.collectAsStateWithLifecycle()
    val period by vm.selectedPeriod.collectAsStateWithLifecycle()
    val actions = LocalVpnActions.current
    var recentOpen by rememberSaveable { mutableStateOf(false) }
    var vpnOpen by rememberSaveable { mutableStateOf(false) }

    PollWhileStarted(DASHBOARD_POLL_MS, repeat = general.autoRefresh, tick = vm::poll)

    val cameras = cameraState.items
    val online = cameras.count(Camera::isOnline)
    val offline = cameras.size - online
    val devices = vpnState.items

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuCard(Modifier.weight(1f).fillMaxHeight(), onClick = { nav.navigate(DevicesRoute) }) {
                    ProgressRing(if (cameras.isEmpty()) 0f else online.toFloat() / cameras.size) {
                        Text("${cameras.size}", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 22.dp))
                    Text("전체 카메라", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("온라인 $online", Modifier.clickable { nav.openTab(MultiviewRoute("online")) }, color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                        Text("오프라인 $offline", Modifier.clickable { nav.openTab(MultiviewRoute("offline")) }, color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                NeuCard(
                    Modifier.weight(1f).fillMaxHeight(),
                    onClick = { nav.openTab(EventsRoute(status = EventStatus.UNCONFIRMED, period = "60d")) }
                ) {
                    BigNumber("${events.unconfirmed}", "건", Modifier.heightIn(min = 72.dp))
                    Spacer(Modifier.weight(1f).heightIn(min = 22.dp))
                    Text("미확인 알림", style = MaterialTheme.typography.titleMedium)
                    Text("확인이 필요한 이벤트", color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }

        item {
            NeuCard(Modifier.fillMaxWidth(), onClick = { nav.openTab(EventsRoute(period = "today")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("오늘 이벤트", style = MaterialTheme.typography.titleMedium)
                        Text(KstTime.today().toString(), color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    BigNumber("${events.today}", "건", style = MaterialTheme.typography.headlineLarge)
                }
            }
        }

        item {
            NeuCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("이벤트 현황", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    ChipRow(StatsPeriod.entries.map { it to it.label }, period, vm::choosePeriod)
                }
                VerticalGap(12)
                EventTypeSummary(events.typeCounts)
            }
        }

        item { CameraStrip(if (general.showOfflineBoards) cameras else cameras.filter(Camera::isOnline), nav) }

        item {
            NeuCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("최근 이벤트", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (events.recent.size > 3) TextAction("전체 ${events.recent.size}건", { recentOpen = true }, color = NeuMuted)
                }
                VerticalGap(4)
                if (events.recent.isEmpty()) Text("최근 이벤트가 없습니다.", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                events.recent.take(3).forEach { event -> RecentRow(event) { nav.switchTab(EventsRoute()) } }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuCard(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(client.status.label, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        DimIconButton(Icons.Default.Refresh, "VPN 연결 확인", actions.reconnect)
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 22.dp))
                    Text("Client VPN", style = MaterialTheme.typography.titleMedium)
                    Text("이 휴대폰", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                }
                NeuCard(Modifier.weight(1f).fillMaxHeight(), onClick = { vpnOpen = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigNumber("${devices.count { it.vpnStatus == VpnStatusText.CONNECTED }}", "/ ${devices.size}", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        Icon(Icons.Default.Tune, null, tint = NeuMuted.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 22.dp))
                    Text("Device VPN", style = MaterialTheme.typography.titleMedium)
                    Text("정상 연결", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DataFreshness(cameraState.source, cameraState.fetchedAt) }
            if (cameras.isEmpty() && cameraState.fetchedAt == null && !cameraState.isFetching) {
                VerticalGap(8)
                NoticeBox("서버에서 카메라 목록을 받지 못했습니다. VPN과 서버 연결을 확인해 주세요.", color = NeuWarning)
            }
        }
    }

    if (recentOpen) CleanSheet(onDismiss = { recentOpen = false }, title = "최근 이벤트") {
        events.recent.forEach { event -> RecentRow(event) { recentOpen = false; nav.switchTab(EventsRoute()) } }
    }

    if (vpnOpen) DeviceVpnSheet(vm, onManage = { vpnOpen = false; nav.navigate(VpnManageRoute) }, onDismiss = { vpnOpen = false })
}

@Composable
private fun RecentRow(event: EventItem, onClick: () -> Unit) {
    ListRowPlain(
        title = "${event.cameraName} · ${EventTypes.label(event.type)}",
        subtitle = KstTime.format(event.occurredAt),
        dot = eventColor(event.type),
        trailing = EventStatus.label(event.status),
        onClick = onClick
    )
}

@Composable
private fun EventTypeSummary(counts: Map<String, Int>) {
    val total = counts.values.sum()
    BigNumber("$total", "건")
    VerticalGap(14)
    Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(NeuSurfaceStrong)) {
        if (total > 0) EventTypes.labels.keys.forEach { type ->
            val count = counts[type] ?: 0
            if (count > 0) Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(eventColor(type)))
        }
    }
    VerticalGap(14)
    EventTypes.labels.toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { (type, label) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(eventColor(type), CircleShape))
                    Text(label, Modifier.weight(1f), color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${counts[type] ?: 0}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CameraStrip(cameras: List<Camera>, nav: NavHostController) {
    NeuCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { nav.switchTab(MultiviewRoute()) }.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("카메라", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "멀티뷰로 이동", tint = NeuMuted.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
        VerticalGap(12)
        if (cameras.isEmpty()) {
            Text("등록된 카메라가 없습니다.", Modifier.padding(horizontal = 20.dp), color = NeuMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cameras, key = { it.mac }) { camera ->
                    Column(Modifier.width(136.dp).clickable { nav.navigate(MonitoringRoute(camera.mac)) }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(NeuSmallShape).background(Color.Black), contentAlignment = Alignment.Center) {
                            val thumbnail = remember(camera.thumbnailUrl) { MediaUrls.resolve(camera.thumbnailUrl) }
                            if (thumbnail != null && camera.isOnline) {
                                AsyncImage(thumbnail, "${camera.name} 미리보기", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.VideocamOff, null, tint = NeuMuted.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                            }
                        }
                        VerticalGap(8)
                        StatusPill(camera.name.ifBlank { camera.deviceId }, if (camera.isOnline) NeuSuccess else NeuMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceVpnSheet(vm: DashboardViewModel, onManage: () -> Unit, onDismiss: () -> Unit) {
    val state by vm.vpnDevices.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }
    val all = state.items
    CleanSheet(onDismiss = onDismiss, title = "Device VPN") {
        listOf(VpnStatusText.CONNECTED, VpnStatusText.DISCONNECTED, VpnStatusText.ERROR).forEach { status ->
            val count = all.count { it.vpnStatus == status }
            val ratio = if (all.isEmpty()) 0 else count * 100 / all.size
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Box(Modifier.weight(1f)) { StatusPill(VpnStatusText.label(status), vpnStatusColor(status)) }
                Text("${count}대 · $ratio%", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        VerticalGap(8)
        ChipRow(
            listOf("all" to "전체", VpnStatusText.CONNECTED to "정상", VpnStatusText.DISCONNECTED to "연결 끊김", VpnStatusText.ERROR to "오류"),
            filter, { filter = it }
        )
        VerticalGap(4)
        sortVpnIssuesFirst(all.filter { filter == "all" || it.vpnStatus == filter }).forEach { device ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(device.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusPill(VpnStatusText.label(device.vpnStatus), vpnStatusColor(device.vpnStatus))
            }
        }
        VerticalGap(12)
        NeuOutlineButton("VPN 연결 관리", onManage, Modifier.fillMaxWidth())
    }
}
