package com.example.androidapplication.feature.vpn

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.CAMERA_POLL_MS
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.common.UiMessageBus
import com.example.androidapplication.core.common.VPN_CHECK_MESSAGE
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.data.RecoveryState
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.data.VpnDevice
import com.example.androidapplication.core.data.VpnRepository
import com.example.androidapplication.core.data.VpnStatusText
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.vpn.ClientVpnConnector
import com.example.androidapplication.core.vpn.ClientVpnState
import com.example.androidapplication.core.vpn.ConnectOutcome
import com.example.androidapplication.core.vpn.VpnMonitor
import com.example.androidapplication.core.vpn.WireGuardApp
import com.example.androidapplication.navigation.LocalVpnActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

fun clientVpnColor(state: ClientVpnState) = when (state) {
    ClientVpnState.Connected -> NeuSuccess
    ClientVpnState.Connecting -> NeuWarning
    ClientVpnState.Error -> NeuDanger
    ClientVpnState.Disconnected -> NeuMuted
}

@HiltViewModel
class VpnCheckViewModel @Inject constructor(private val connector: ClientVpnConnector) : ViewModel() {
    private val _result = MutableStateFlow<Boolean?>(null)
    val result = _result.asStateFlow()
    private val consentChannel = Channel<android.content.Intent>(Channel.CONFLATED)
    val consent = consentChannel.receiveAsFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        run()
    }

    fun run() {
        viewModelScope.launch {
            when (val outcome = connector.connectAndCheck()) {
                ConnectOutcome.Connected -> _result.value = true
                ConnectOutcome.Failed -> _result.value = false
                is ConnectOutcome.NeedsConsent -> consentChannel.trySend(outcome.intent)
            }
        }
    }

    fun onConsentDenied() {
        _result.value = false
    }
}

/** 로그인 성공 또는 저장된 세션 → VPN 확인 → 성공 시 대시보드, 실패 시 VPN 연결 관리. */
@Composable
fun VpnCheckScreen(onDone: (Boolean) -> Unit, vm: VpnCheckViewModel = hiltViewModel()) {
    val result by vm.result.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) vm.run() else vm.onConsentDenied()
    }
    LaunchedEffect(Unit) {
        vm.start()
        vm.consent.collect(launcher::launch)
    }
    LaunchedEffect(result) { result?.let(onDone) }

    FullScreenMessage {
        CircularProgressIndicator(color = NeuAccent, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
        VerticalGap(20)
        Text("VPN 연결 확인 중", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun VpnBanner(onGuide: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 8.dp).surfaceCard(NeuFieldShape).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(VPN_CHECK_MESSAGE, NeuDanger, Modifier.weight(1f).clickable(onClick = onGuide))
        TextAction("재연결 안내", onGuide, color = NeuMuted)
        TextAction("다시 확인", onRetry)
        DimIconButton(Icons.Default.Close, "안내 닫기", onDismiss)
    }
}

@Composable
fun VpnGuideSheet(status: ClientVpnState, onCheck: () -> Unit, onManage: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    CleanSheet(onDismiss = onDismiss, title = "VPN 재연결 안내") {
        WireGuardApp.guideSteps.forEachIndexed { index, (title, body) ->
            Text("${index + 1}. $title", style = MaterialTheme.typography.titleMedium)
            Text(body, color = NeuMuted, style = MaterialTheme.typography.bodySmall)
            VerticalGap(14)
        }
        if (status != ClientVpnState.Disconnected) {
            StatusPill(if (status == ClientVpnState.Connecting) "연결 상태를 확인하는 중" else status.label, clientVpnColor(status))
            VerticalGap(14)
        }
        PrimaryButton("연결 다시 확인", onCheck, Modifier.fillMaxWidth())
        VerticalGap(8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuOutlineButton("WireGuard 앱 열기", { WireGuardApp.open(context) }, Modifier.weight(1f))
            NeuOutlineButton("VPN 연결 관리", onManage, Modifier.weight(1f))
        }
    }
}

@HiltViewModel
class VpnManageViewModel @Inject constructor(
    private val repository: VpnRepository,
    private val bus: UiMessageBus,
    monitor: VpnMonitor,
    settings: SettingsRepository
) : ViewModel() {
    val devices = repository.devices
    val client = monitor.ui
    val general = settings.general

    suspend fun poll(first: Boolean) = try {
        repository.refresh(silent = !first)
    } catch (e: AppError) {
        bus.showError(e, "VPN 장치 목록 조회 실패")
    }

    fun refresh() {
        viewModelScope.launch { poll(first = true) }
    }

    fun recover(mac: String) {
        viewModelScope.launch {
            try {
                repository.recover(mac)
                bus.show("복구를 요청했습니다.")
            } catch (e: AppError) {
                bus.showError(e, "VPN 복구 요청 실패")
            }
        }
    }
}

fun filterVpnDevices(devices: List<VpnDevice>, keyword: String, status: String): List<VpnDevice> {
    val query = keyword.trim().lowercase()
    return devices.filter { device ->
        (status == "all" || device.vpnStatus == status) &&
            (query.isEmpty() || device.name.lowercase().contains(query) || device.serial.orEmpty().lowercase().contains(query))
    }
}

private val STATUS_FILTERS = listOf(
    "all" to "전체", VpnStatusText.CONNECTED to "정상", VpnStatusText.DISCONNECTED to "연결 끊김", VpnStatusText.ERROR to "오류"
)

@Composable
fun VpnManageScreen(vm: VpnManageViewModel = hiltViewModel()) {
    val state by vm.devices.collectAsStateWithLifecycle()
    val client by vm.client.collectAsStateWithLifecycle()
    val general by vm.general.collectAsStateWithLifecycle()
    val actions = LocalVpnActions.current
    var keyword by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("all") }
    var selectedMac by rememberSaveable { mutableStateOf<String?>(null) }

    PollWhileStarted(CAMERA_POLL_MS, repeat = general.autoRefresh, tick = vm::poll)

    val filtered = remember(state.items, keyword, status) { filterVpnDevices(state.items, keyword, status) }
    val items = state.items

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuCard(Modifier.weight(1f).fillMaxHeight(), onClick = actions.showGuide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(client.status.label, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                        DimIconButton(Icons.Default.Refresh, "재연결", actions.reconnect)
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
                    Text("Client VPN", style = MaterialTheme.typography.titleMedium)
                    Text("이 휴대폰", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                }
                NeuCard(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigNumber("${items.count { it.vpnStatus == VpnStatusText.CONNECTED }}", "/ ${items.size}", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        DimIconButton(Icons.Default.Refresh, "새로고침", vm::refresh)
                    }
                    Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
                    Text("Device VPN", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "끊김 ${items.count { it.vpnStatus == VpnStatusText.DISCONNECTED }} · 오류 ${items.count { it.vpnStatus == VpnStatusText.ERROR }}",
                        color = NeuMuted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        item {
            SearchField(keyword, { keyword = it }, "이름 또는 시리얼 검색")
            VerticalGap(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipRow(STATUS_FILTERS, status, { status = it }, Modifier.weight(1f))
                if (keyword.isNotEmpty() || status != "all") TextAction("초기화", { keyword = ""; status = "all" }, color = NeuMuted)
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.WifiOff,
                    if (items.isEmpty()) "표시할 VPN 연결이 없습니다." else "조건에 맞는 기기가 없습니다.",
                    if (items.isEmpty()) "서버에 연결되어 있는지 확인해 주세요." else null
                )
            }
        } else item {
            ListGroup {
                filtered.forEachIndexed { index, device ->
                    ListRow(
                        title = device.name.ifBlank { Mac.format(device.mac) },
                        subtitle = device.vpnIp ?: "-",
                        divider = index != filtered.lastIndex,
                        onClick = { selectedMac = device.mac },
                        trailing = {
                            if (device.recovery == RecoveryState.Failed) TextAction("복구 요청", { vm.recover(device.mac) })
                            else StatusPill(VpnStatusText.label(device.vpnStatus), vpnStatusColor(device.vpnStatus))
                        }
                    )
                }
            }
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DataFreshness(state.source, state.fetchedAt) } }
    }

    val selected = items.firstOrNull { it.mac == selectedMac }
    if (selected != null) CleanSheet(onDismiss = { selectedMac = null }, title = selected.name) {
        InfoRow("VPN 상태", VpnStatusText.label(selected.vpnStatus), valueColor = vpnStatusColor(selected.vpnStatus))
        InfoRow("VPN IP", selected.vpnIp ?: "-")
        InfoRow("최근 Handshake", KstTime.format(selected.lastHandshake))
        InfoRow("연결 시간", KstTime.formatDuration(selected.durationSec))
        InfoRow("VPN 서버", selected.server ?: "-")
        InfoRow("시리얼 번호", selected.serial?.ifBlank { "-" } ?: "-")
        InfoRow("자동 복구", selected.recovery.label)
        selected.error?.let {
            InfoRow("오류", it, valueColor = NeuDanger)
            VerticalGap(6)
            NoticeBox("보드의 네트워크 연결과 방화벽(UDP ${ApiConfig.VPN_PORT}) 설정을 점검한 뒤 복구를 요청할 수 있습니다.")
        }
        if (selected.recovery == RecoveryState.Failed) {
            VerticalGap(14)
            PrimaryButton("복구 요청", { vm.recover(selected.mac); selectedMac = null }, Modifier.fillMaxWidth())
        }
    }
}
