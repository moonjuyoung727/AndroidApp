package com.example.androidapplication.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.POLL_RETRY_MS
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.common.formatBytes
import com.example.androidapplication.core.config.PrivacyZoneLimits
import com.example.androidapplication.core.data.*
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.media.LocalStorageSettings
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.VideoLayer
import com.example.androidapplication.feature.auth.PasswordField
import com.example.androidapplication.navigation.*

@Composable
private fun SettingsColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun SettingsScreen(nav: NavHostController) = SettingsColumn {
    ListGroup {
        ListRow("일반", icon = Icons.Default.Tune, onClick = { nav.navigate(GeneralSettingsRoute) })
        ListRow("알림", icon = Icons.Default.Notifications, onClick = { nav.navigate(NotificationSettingsRoute) })
        ListRow("카메라 설정", icon = Icons.Default.Videocam, onClick = { nav.navigate(CameraSettingsRoute()) })
        ListRow("프라이버시 존", icon = Icons.Default.Shield, divider = false, onClick = { nav.navigate(PrivacyZoneRoute()) })
    }
    ListGroup {
        ListRow("이 기기 저장", icon = Icons.Default.PhoneAndroid, onClick = { nav.navigate(LocalStorageRoute) })
        ListRow("클라우드 저장", icon = Icons.Default.Cloud, divider = false, onClick = { nav.navigate(CloudStorageRoute) })
    }
    ListGroup {
        ListRow("구독", icon = Icons.Default.CreditCard, onClick = { nav.navigate(SubscriptionRoute) })
        ListRow("사용자 정보", icon = Icons.Default.Person, divider = false, onClick = { nav.navigate(AccountRoute) })
    }
}

@Composable
fun GeneralSettingsScreen(vm: GeneralSettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsColumn {
        ListGroup {
            SettingToggle("자동 새로고침", null, s.autoRefresh, { vm.update(s.copy(autoRefresh = it)) })
            SettingToggle("오프라인 보드 표시", null, s.showOfflineBoards, { vm.update(s.copy(showOfflineBoards = it)) })
            SettingSelect("기본 멀티뷰 채널", null, listOf(2 to "2채널", 4 to "4채널", 6 to "6채널"), s.defaultChannel, { vm.update(s.copy(defaultChannel = it)) })
        }
    }
}

@Composable
fun NotificationSettingsScreen(vm: NotificationSettingsViewModel = hiltViewModel()) {
    val s by vm.server.collectAsStateWithLifecycle()
    val l by vm.localSettings.collectAsStateWithLifecycle()
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    PollWhileStarted(POLL_RETRY_MS, repeat = false, tick = vm::load)
    SettingsColumn {
        ListGroup {
            SettingToggle("푸시 알림", null, l.pushEnabled, { vm.updateLocal(l.copy(pushEnabled = it)) })
            SettingToggle("소리", null, l.sound, { vm.updateLocal(l.copy(sound = it)) }, enabled = l.pushEnabled)
            SettingToggle("진동", null, l.vibrate, { vm.updateLocal(l.copy(vibrate = it)) }, enabled = l.pushEnabled)
        }
        ListGroup {
            SettingToggle("움직임 감지", null, s.motion, { vm.update(s.copy(motion = it)) })
            SettingToggle("소리 감지", null, s.sound, { vm.update(s.copy(sound = it)) })
            SettingToggle("보드 오프라인", null, s.offline, { vm.update(s.copy(offline = it)) })
            SettingToggle("보드 재연결", null, s.reconnect, { vm.update(s.copy(reconnect = it)) })
            SettingToggle("VPN 장애", null, l.vpn, { vm.updateLocal(l.copy(vpn = it)) })
            SettingToggle("중요 이벤트만", null, s.importantOnly, { vm.update(s.copy(importantOnly = it)) })
        }
        if (cameras.items.isNotEmpty()) {
            Text("기기별 알림", Modifier.padding(start = 4.dp), color = NeuMuted, style = MaterialTheme.typography.labelLarge)
            ListGroup {
                cameras.items.forEach { camera ->
                    SettingToggle(
                        camera.name.ifBlank { camera.deviceId }, null,
                        s.deviceAlerts.firstOrNull { it.mac == camera.mac }?.enabled ?: true,
                        { vm.setDevice(camera.mac, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionScreen(vm: SubscriptionViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val info = ui.info

    SettingsColumn {
        NeuCard(Modifier.fillMaxWidth()) {
            Text(info?.plan ?: "-", style = MaterialTheme.typography.headlineLarge)
            VerticalGap(4)
            Text(
                listOfNotNull(
                    info?.status?.let { if (it == "active") "이용 중" else "만료" },
                    info?.nextPaymentDate?.let { "다음 결제일 $it" }
                ).joinToString(" · ").ifBlank { "현재 요금제" },
                color = NeuMuted, style = MaterialTheme.typography.bodySmall
            )
        }
        ListGroup {
            SettingToggle("자동 갱신", null, info?.autoRenew == true, vm::setAutoRenew, enabled = info != null)
            ListRow("요금제 변경", divider = false, enabled = info != null, onClick = { planOpen = true })
        }
        if (ui.methods.isNotEmpty()) {
            Text("결제 수단", Modifier.padding(start = 4.dp), color = NeuMuted, style = MaterialTheme.typography.labelLarge)
            ListGroup {
                ui.methods.forEachIndexed { index, method ->
                    ListRow(
                        "${method.brand} •••• ${method.last4}", subtitle = "만료 ${method.expiry}",
                        divider = index != ui.methods.lastIndex,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (method.isDefault) StatusPill("기본", NeuSuccess) else TextAction("기본 지정", { vm.setDefault(method.id) })
                                TextAction("삭제", { deleteId = method.id }, color = NeuDanger, enabled = !method.isDefault)
                            }
                        }
                    )
                }
            }
        }
        if (!vm.enabled) NoticeBox("구독 정보는 서버 연동 후 사용할 수 있습니다.")
        if (!vm.paymentsEnabled) NoticeBox("결제 수단은 서버 연동과 결제 정책 검토 후 사용할 수 있습니다.")
    }

    if (planOpen && info != null) CleanSheet(onDismiss = { planOpen = false }, title = "요금제 변경") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SubscriptionPlans.all.forEach { plan ->
                val current = plan.name == info.plan
                NeuCard(Modifier.fillMaxWidth(), onClick = if (current) null else ({ planOpen = false; vm.changePlan(plan.id) })) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plan.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        Text("%,d원".format(plan.price), style = MaterialTheme.typography.titleMedium)
                    }
                    VerticalGap(4)
                    Text(plan.features.joinToString(" · "), color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                    if (current) {
                        VerticalGap(8)
                        StatusPill("현재 요금제", NeuSuccess)
                    }
                }
            }
        }
    }
    deleteId?.let { id ->
        ConfirmDialog("결제 수단 삭제", "이 결제 수단을 삭제할까요?", "삭제", onConfirm = { vm.delete(id); deleteId = null }, onDismiss = { deleteId = null }, danger = true)
    }
}

@Composable
fun CloudStorageScreen(vm: CloudStorageViewModel = hiltViewModel()) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val enabled = vm.enabled
    val sub = enabled && d.autoSave
    val estimate = d.quality.gbPerDay * d.retentionDays * (if (d.eventMode) 0.25 else 1.0)

    SettingsColumn {
        NeuCard(Modifier.fillMaxWidth()) {
            BigNumber("%.1f".format(d.usedGb), "/ %.0f GB".format(d.totalGb), style = MaterialTheme.typography.headlineLarge)
            VerticalGap(12)
            val ratio = if (d.totalGb > 0) (d.usedGb / d.totalGb).toFloat().coerceIn(0f, 1f) else 0f
            LinearProgressIndicator({ ratio }, Modifier.fillMaxWidth(), color = NeuAccent, trackColor = NeuSurfaceStrong, gapSize = 0.dp, drawStopIndicator = {})
        }
        ListGroup {
            SettingToggle("자동 저장", null, d.autoSave, { v -> vm.edit { it.copy(autoSave = v) } }, enabled = enabled)
            SettingSelect("저장 방식", null, listOf(true to "이벤트 발생 시", false to "상시 녹화"), d.eventMode, { v -> vm.edit { it.copy(eventMode = v) } }, enabled = sub)
            SettingSelect("화질", null, CloudQuality.entries.map { it to it.label }, d.quality, { v -> vm.edit { it.copy(quality = v) } }, enabled = sub)
            SettingSelect("보관 기간", null, listOf(7, 14, 30, 60, 90).map { it to "${it}일" }, d.retentionDays, { v -> vm.edit { it.copy(retentionDays = v) } }, enabled = sub)
            SettingToggle("용량 부족 시 덮어쓰기", null, d.overwriteWhenFull, { v -> vm.edit { it.copy(overwriteWhenFull = v) } }, enabled = enabled)
        }
        NoticeBox("예상 사용량: 카메라 1대당 약 %.1f GB".format(estimate))
        if (!enabled) NoticeBox("클라우드 저장 설정은 서버 연동 후 사용할 수 있습니다.")
        PrimaryButton(if (saving) "저장 중..." else "변경 사항 저장", vm::save, Modifier.fillMaxWidth(), enabled = enabled && d != saved && !saving)
    }
}

@Composable
fun LocalStorageScreen(vm: LocalStorageViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val u by vm.usage.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    val total = formatBytes(u.totalBytes).split(' ')

    SettingsColumn {
        NeuCard(Modifier.fillMaxWidth()) {
            BigNumber(total[0], total.getOrNull(1), style = MaterialTheme.typography.headlineLarge)
            VerticalGap(4)
            Text("사진 ${formatBytes(u.imageBytes)} · 동영상 ${formatBytes(u.videoBytes)} · 남은 공간 ${formatBytes(u.freeBytes)}", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
        }
        ListGroup {
            SettingSelect("보관 기간", null, LocalStorageSettings.RETENTION_OPTIONS.map { it to if (it == 0) "제한 없음" else "${it}일" }, s.retentionDays, { vm.update(s.copy(retentionDays = it)) })
            SettingSelect("용량 상한", null, LocalStorageSettings.CAP_OPTIONS.map { it to if (it == 0L) "제한 없음" else "${it / LocalStorageSettings.GB}GB" }, s.capBytes, { vm.update(s.copy(capBytes = it)) })
            SettingToggle("상한 초과 시 오래된 파일 삭제", null, s.deleteOldestOverCap, { vm.update(s.copy(deleteOldestOverCap = it)) })
        }
        ListGroup { ListRow("전체 삭제", titleColor = NeuDanger, divider = false, showChevron = false, onClick = { confirm = true }) }
        NoticeBox("파일은 앱 전용 저장소에 보관되며 앱을 삭제하면 함께 삭제됩니다. 필요한 파일은 갤러리로 내보내 주세요.")
    }

    if (confirm) ConfirmDialog(
        "전체 삭제", "이 계정의 저장 파일을 모두 삭제할까요? 삭제하면 되돌릴 수 없습니다.", "전체 삭제",
        onConfirm = { confirm = false; vm.deleteAll() }, onDismiss = { confirm = false }, danger = true
    )
}

@Composable
fun CameraSettingsScreen(nav: NavHostController, vm: CameraSettingsViewModel = hiltViewModel()) {
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    var mac by rememberSaveable { mutableStateOf(vm.initialMac) }
    val selected = cameras.items.firstOrNull { it.mac == mac } ?: cameras.items.firstOrNull()
    var name by rememberSaveable(selected?.mac) { mutableStateOf(selected?.name.orEmpty()) }
    var description by rememberSaveable(selected?.mac) { mutableStateOf(selected?.description.orEmpty()) }
    val error = Validators.cameraName(name)

    SettingsColumn {
        if (selected == null) {
            EmptyState(Icons.Default.Videocam, "등록된 카메라가 없습니다.")
            return@SettingsColumn
        }
        ListGroup {
            SettingSelect("카메라", null, cameras.items.map { it.mac to it.name.ifBlank { it.deviceId } }, selected.mac, { mac = it })
            ListRow("프라이버시 존", divider = false, onClick = { nav.navigate(PrivacyZoneRoute(selected.mac)) })
        }
        NeuTextField(name, { name = it.take(Validators.CAMERA_NAME_MAX) }, "이름", error = error, counter = "${name.length}/${Validators.CAMERA_NAME_MAX}", enabled = vm.canUpdate)
        NeuTextField(
            description, { description = it.take(Validators.CAMERA_DESCRIPTION_MAX) }, "설명",
            singleLine = false, minLines = 3, counter = "${description.length}/${Validators.CAMERA_DESCRIPTION_MAX}", enabled = vm.canUpdate
        )
        if (vm.canUpdate) PrimaryButton("저장", { vm.save(selected.mac, name, description) }, Modifier.fillMaxWidth(), enabled = error == null)
        else NoticeBox("카메라 이름과 설명 수정은 서버 연동 후 사용할 수 있습니다.")
    }
}

private const val NEEDS_SERVER = "서버 연동 후 사용 가능"

@Composable
fun AccountScreen(nav: NavHostController, onLogout: () -> Unit, vm: AccountViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val remember by vm.remember.collectAsStateWithLifecycle()
    var nameOpen by rememberSaveable { mutableStateOf(false) }
    var passwordOpen by rememberSaveable { mutableStateOf(false) }
    var deleteOpen by rememberSaveable { mutableStateOf(false) }

    SettingsColumn {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
            Text(session?.name?.ifBlank { "-" } ?: "-", style = MaterialTheme.typography.headlineLarge)
            Text(session?.email?.ifBlank { "-" } ?: "-", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
        }
        ListGroup {
            ListRow("이름 변경", subtitle = if (vm.canUpdateProfile) null else NEEDS_SERVER, enabled = vm.canUpdateProfile, onClick = { nameOpen = true })
            ListRow("비밀번호 변경", subtitle = if (vm.canChangePassword) null else NEEDS_SERVER, enabled = vm.canChangePassword, onClick = { passwordOpen = true })
            ListRow("로그인 기기", enabled = vm.canManageSessions, divider = false, onClick = { nav.navigate(LoginSessionsRoute) })
        }
        ListGroup { SettingToggle("로그인 상태 유지", null, remember, vm::setRemember) }
        ListGroup {
            ListRow("로그아웃", icon = Icons.AutoMirrored.Filled.ExitToApp, titleColor = NeuDanger, showChevron = false, onClick = onLogout)
            ListRow("회원 탈퇴", subtitle = if (vm.canDeleteAccount) null else NEEDS_SERVER, titleColor = NeuDanger, enabled = vm.canDeleteAccount, divider = false, showChevron = false, onClick = { deleteOpen = true })
        }
    }

    if (nameOpen) NameSheet(session?.name.orEmpty(), onDismiss = { nameOpen = false }) {
        nameOpen = false
        vm.updateName(it)
    }
    if (passwordOpen) PasswordSheet(onDismiss = { passwordOpen = false }) { current, next ->
        vm.changePassword(current, next) { passwordOpen = false }
    }
    if (deleteOpen) DeleteAccountDialog(onDismiss = { deleteOpen = false }) { pw, reason ->
        deleteOpen = false
        vm.deleteAccount(pw, reason)
    }
}

@Composable
private fun NameSheet(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val error = Validators.name(name)
    CleanSheet(onDismiss = onDismiss, title = "이름 변경") {
        NeuTextField(name, { name = it.take(Validators.NAME_MAX) }, "이름", error = error)
        VerticalGap(16)
        PrimaryButton("저장", { onSave(name) }, Modifier.fillMaxWidth().imePadding(), enabled = error == null && name != initial)
    }
}

@Composable
private fun PasswordSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }
    CleanSheet(onDismiss = onDismiss, title = "비밀번호 변경") {
        PasswordField(current, { current = it }, "현재 비밀번호", errors["current"])
        VerticalGap(10)
        PasswordField(next, { next = Validators.filterPasswordInput(it) }, "새 비밀번호", errors["new"])
        VerticalGap(10)
        PasswordField(confirm, { confirm = Validators.filterPasswordInput(it) }, "새 비밀번호 확인", errors["confirm"])
        VerticalGap(16)
        PrimaryButton("변경", {
            errors = buildMap {
                if (current.isEmpty()) put("current", "현재 비밀번호를 입력해 주세요.")
                Validators.password(next)?.let { put("new", it) }
                Validators.passwordConfirm(next, confirm)?.let { put("confirm", it) }
            }
            if (errors.isEmpty()) onSave(current, next)
        }, Modifier.fillMaxWidth().imePadding())
    }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuBg,
        shape = NeuCardShape,
        title = { Text("회원 탈퇴", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("계정과 관련 데이터가 삭제되며 되돌릴 수 없습니다.", color = NeuMuted, style = MaterialTheme.typography.bodySmall)
                VerticalGap(10)
                PasswordField(password, { password = it }, "비밀번호", null)
                VerticalGap(10)
                NeuTextField(reason, { reason = it.take(200) }, "탈퇴 사유 (선택)")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password, reason.ifBlank { null }) }, enabled = password.isNotEmpty()) { Text("탈퇴", color = NeuDanger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = NeuMuted) } }
    )
}

@Composable
fun LoginSessionsScreen(vm: LoginSessionsViewModel = hiltViewModel()) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var confirmOthers by rememberSaveable { mutableStateOf(false) }
    PollWhileStarted(POLL_RETRY_MS, repeat = false, tick = vm::poll)
    SettingsColumn {
        if (sessions.isEmpty()) {
            EmptyState(Icons.Default.Devices, if (loading) "불러오는 중..." else "표시할 로그인 기기가 없습니다.")
        } else ListGroup {
            sessions.forEachIndexed { index, s ->
                ListRow(
                    s.clientName.ifBlank { "알 수 없는 기기" },
                    subtitle = "${s.ipAddress.ifBlank { "-" }} · ${KstTime.format(s.lastActiveAt)}",
                    divider = index != sessions.lastIndex,
                    trailing = {
                        if (s.isCurrent) StatusPill("현재 기기", NeuSuccess)
                        else TextAction("로그아웃", { vm.revoke(s.id) }, color = NeuDanger)
                    }
                )
            }
        }
        if (sessions.any { !it.isCurrent }) {
            NeuOutlineButton("다른 기기 모두 로그아웃", { confirmOthers = true }, Modifier.fillMaxWidth(), color = NeuDanger)
        }
    }
    if (confirmOthers) ConfirmDialog("다른 기기 로그아웃", "현재 기기를 제외한 모든 기기를 로그아웃할까요?", "로그아웃", onConfirm = { confirmOthers = false; vm.revokeOthers() }, onDismiss = { confirmOthers = false }, danger = true)
}

@Composable
fun PrivacyZoneScreen(nav: NavHostController, vm: PrivacyZoneViewModel = hiltViewModel()) {
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    val mac by vm.mac.collectAsStateWithLifecycle()
    val zones by vm.draft.collectAsStateWithLifecycle()
    val dirty by vm.isDirty.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val stream by vm.streamState.collectAsStateWithLifecycle()
    val frame by vm.frameSize.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf(true) }
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragNow by remember { mutableStateOf<Offset?>(null) }

    PollWhileStarted(vm.pollInterval, tick = vm::poll)
    LifecycleStartEffect(vm) {
        vm.setStarted(true)
        onStopOrDispose { vm.setStarted(false) }
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    val camera = cameras.items.firstOrNull { it.mac == mac }
    val ratio = frame?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (16f / 9f)

    SettingsColumn {
        if (cameras.items.isEmpty()) {
            EmptyState(Icons.Default.Videocam, "등록된 카메라가 없습니다.")
            return@SettingsColumn
        }
        ListGroup {
            SettingSelect(
                "카메라", null,
                cameras.items.map { it.mac to it.name.ifBlank { it.deviceId } }, mac,
                { target -> if (dirty) pendingCamera = target else vm.select(target!!) }
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(NeuCardShape)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragStart = it; dragNow = it },
                        onDrag = { change, _ -> dragNow = change.position; change.consume() },
                        onDragEnd = {
                            val a = dragStart
                            val b = dragNow
                            if (a != null && b != null) vm.addZone(a.x / size.width, a.y / size.height, b.x / size.width, b.y / size.height)
                            dragStart = null
                            dragNow = null
                        },
                        onDragCancel = { dragStart = null; dragNow = null }
                    )
                }
        ) {
            if (session != null) VideoLayer(session, vm.eglContext, if (preview) zones else emptyList(), Modifier.fillMaxSize())
            ZoneOutlines(zones, dragStart, dragNow)
            if (stream != StreamState.Connected) {
                Text(
                    when {
                        camera?.isOnline == false -> "오프라인"
                        stream == StreamState.Idle -> "스트리밍 없음"
                        else -> "영상 연결 중..."
                    },
                    Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall
                )
            }
        }
        NoticeBox("영상 위를 드래그해 가릴 영역을 추가하세요. (최대 ${PrivacyZoneLimits.MAX_ZONES}개)")

        ListGroup {
            SettingToggle("마스킹 미리보기", null, preview, { preview = it })
            zones.forEach { zone ->
                HorizontalDivider(Modifier.padding(start = 20.dp), color = NeuLine)
                Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeuTextField(zone.name, { v -> vm.editZone(zone.id) { it.copy(name = v.take(PrivacyZoneLimits.NAME_MAX)) } }, "이름", Modifier.weight(1f))
                        DimIconButton(Icons.Default.Close, "${zone.name} 삭제", { vm.removeZone(zone.id) }, tint = NeuDanger)
                    }
                }
                SettingToggle("활성화", null, zone.enabled, { v -> vm.editZone(zone.id) { it.copy(enabled = v) } })
            }
        }

        result?.let { NoticeBox(it, color = if (it == "저장되었습니다.") NeuSuccess else NeuDanger) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuOutlineButton("되돌리기", vm::revert, Modifier.weight(1f), enabled = dirty && !saving)
            PrimaryButton(if (saving) "저장 중..." else "저장", vm::save, Modifier.weight(1f), enabled = dirty && !saving)
        }
        VerticalGap(4)
    }

    pendingCamera?.let { target ->
        ConfirmDialog("저장되지 않음", "저장하지 않은 변경 사항이 있습니다. 이동하시겠습니까?", "이동", onConfirm = { vm.select(target); pendingCamera = null }, onDismiss = { pendingCamera = null })
    }
    if (confirmLeave) ConfirmDialog("저장되지 않음", "저장하지 않은 변경 사항이 있습니다. 나가시겠습니까?", "나가기", onConfirm = { confirmLeave = false; nav.popBackStack() }, onDismiss = { confirmLeave = false })
}

@Composable
private fun BoxScope.ZoneOutlines(zones: List<PrivacyZone>, dragStart: Offset?, dragNow: Offset?) {
    Canvas(Modifier.matchParentSize()) {
        zones.forEach { z ->
            drawRect(NeuAccent, Offset(z.x * size.width, z.y * size.height), Size(z.width * size.width, z.height * size.height), style = Stroke(1.5.dp.toPx()))
        }
        if (dragStart != null && dragNow != null) {
            drawRect(
                NeuWarning,
                Offset(minOf(dragStart.x, dragNow.x), minOf(dragStart.y, dragNow.y)),
                Size(kotlin.math.abs(dragStart.x - dragNow.x), kotlin.math.abs(dragStart.y - dragNow.y)),
                style = Stroke(1.5.dp.toPx())
            )
        }
    }
}
