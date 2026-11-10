package com.example.androidapplication.feature.monitoring

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.androidapplication.core.common.CAMERA_POLL_MS
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.common.formatBytes
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.ListSource
import com.example.androidapplication.core.data.VpnStatusText
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.VideoLayer
import com.example.androidapplication.feature.devices.RenameDialog
import com.example.androidapplication.navigation.CameraSettingsRoute
import com.example.androidapplication.navigation.LocalShellChrome
import com.example.androidapplication.navigation.MediaDetailRoute
import com.example.androidapplication.navigation.PrivacyZoneRoute
import com.example.androidapplication.navigation.rememberKstClock

private val StageShape = NeuCardShape

@Composable
fun MonitoringScreen(nav: NavHostController, vm: MonitoringViewModel = hiltViewModel()) {
    val camera by vm.camera.collectAsStateWithLifecycle()
    val list by vm.cameraList.collectAsStateWithLifecycle()
    val general by vm.general.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val chrome = LocalShellChrome.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    PollWhileStarted(CAMERA_POLL_MS, repeat = general.autoRefresh, tick = vm::poll)

    // ON_STOP이면 스트림을 끊고 진행 중인 녹화를 저장한다
    LifecycleStartEffect(vm) {
        vm.setStarted(true)
        onStopOrDispose { vm.setStarted(false) }
    }

    val activity = LocalActivity.current
    DisposableEffect(recording.active) {
        if (recording.active) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    if (fullscreen) {
        ImmersiveLandscape()
        DisposableEffect(Unit) {
            chrome.hidden = true
            onDispose { chrome.hidden = false }
        }
    }
    BackHandler(enabled = fullscreen) { vm.setFullscreen(false) }
    BackHandler(enabled = !fullscreen && recording.active) { confirmLeave = true }

    val current = camera
    if (current == null) {
        FullScreenMessage {
            Text(if (list.source == ListSource.Loading) "카메라 정보를 불러오는 중..." else "찾을 수 없는 카메라입니다.", color = NeuMuted)
        }
        return
    }

    val onViewSaved: (Long) -> Unit = { nav.navigate(MediaDetailRoute(it)) }
    if (fullscreen) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            VideoStage(vm, current, Modifier.fillMaxSize(), rounded = false)
            ControlRow(vm, onViewSaved, Modifier.align(Alignment.BottomCenter).padding(12.dp), overVideo = true)
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 가로 화면에서도 영상 전체가 한 화면에 들어오게 한다
            val maxStageHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
            VideoStage(vm, current, Modifier.fillMaxWidth().heightIn(max = maxStageHeight), rounded = true)
            Header(current, vm)
            ControlRow(vm, onViewSaved, Modifier.fillMaxWidth(), overVideo = false)
            InfoCard(current, vm, nav)
        }
    }

    if (confirmLeave) ConfirmDialog(
        title = "녹화 중",
        text = "녹화를 종료하고 나갈까요?",
        confirmLabel = "나가기",
        danger = true,
        onConfirm = { confirmLeave = false; nav.popBackStack() },
        onDismiss = { confirmLeave = false }
    )
}

@Composable
private fun Header(camera: Camera, vm: MonitoringViewModel) {
    val stream by vm.streamState.collectAsStateWithLifecycle()
    Column {
        Text(camera.name.ifBlank { camera.deviceId }, style = MaterialTheme.typography.headlineSmall)
        VerticalGap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stream == StreamState.Connected) StatusPill("LIVE", NeuDanger)
            StatusPill(if (camera.isOnline) "Online" else "Offline", if (camera.isOnline) NeuSuccess else NeuMuted)
            StatusPill("VPN ${VpnStatusText.label(camera.vpnStatus)}", vpnStatusColor(camera.vpnStatus))
        }
    }
}

@Composable
private fun VideoStage(vm: MonitoringViewModel, camera: Camera, modifier: Modifier, rounded: Boolean) {
    val session by vm.session.collectAsStateWithLifecycle()
    val stream by vm.streamState.collectAsStateWithLifecycle()
    val reconnecting by vm.reconnecting.collectAsStateWithLifecycle()
    val size by vm.frameSize.collectAsStateWithLifecycle()
    val zones by vm.zones.collectAsStateWithLifecycle()
    val zoom by vm.zoom.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val clock = rememberKstClock()
    val flash = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        vm.shutter.collect {
            flash.snapTo(0.8f)
            flash.animateTo(0f, tween(220))
        }
    }

    // 스테이지 비율을 실제 프레임 비율에 맞춰 마스크가 어긋나지 않게 한다
    val ratio = size?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: (16f / 9f)
    val currentZoom by rememberUpdatedState(zoom)

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(ratio)
                .then(if (rounded) Modifier.clip(StageShape) else Modifier.clipToBounds())
                .background(Color.Black)
                .zoomGestures(view = { currentZoom }, onChange = vm::setZoom)
        ) {
            if (session != null) VideoLayer(session, vm.eglContext, zones, Modifier.fillMaxSize().zoomLayer(zoom))

            // 아래 오버레이는 화면에만 그려지고 저장본에는 들어가지 않는다
            Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recording.active) {
                    StatusPill("REC ${KstTime.formatElapsed(recording.elapsedMs)} · ${formatBytes(recording.sizeBytes)}", Color(0xFFFF5252))
                } else if (stream == StreamState.Connected) {
                    StatusPill("LIVE", NeuDanger)
                }
            }
            Text(clock.substring(11), Modifier.align(Alignment.TopEnd).padding(14.dp), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            if (zoom.isZoomed) Text("${"%.1f".format(zoom.scale)}x", Modifier.align(Alignment.BottomEnd).padding(14.dp), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)

            val message = when {
                !camera.isOnline -> "오프라인\n마지막 수신 ${KstTime.formatLastSeen(camera.lastSeen)}"
                stream == StreamState.Connected -> null
                reconnecting || stream == StreamState.Failed -> "재연결 중..."
                stream == StreamState.Connecting -> "영상 연결 중..."
                stream == StreamState.Idle -> "스트리밍 없음"
                else -> null
            }
            if (message != null) Text(message, Modifier.align(Alignment.Center).padding(16.dp), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (flash.value > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }
    }
}

/** 자주 쓰는 동작만 두고 화질·볼륨·줌은 시트로 옮겼다. */
@Composable
private fun ControlRow(vm: MonitoringViewModel, onViewSaved: (Long) -> Unit, modifier: Modifier, overVideo: Boolean) {
    val stream by vm.streamState.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val available by vm.zonesAvailable.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    val block = vm.saveBlockReason(stream, quality, available)
    val bg = if (overVideo) Color.Black.copy(alpha = 0.5f) else NeuSurfaceStrong
    val tint = if (overVideo) Color.White else NeuAccent

    Column(modifier) {
        Row(
            if (overVideo) Modifier else Modifier.fillMaxWidth(),
            horizontalArrangement = if (overVideo) Arrangement.spacedBy(10.dp) else Arrangement.SpaceBetween
        ) {
            CircleIconButton(Icons.Default.PhotoCamera, "스냅샷", { vm.snapshot(onViewSaved) }, enabled = block == null, tint = tint, background = bg)
            CircleIconButton(
                if (recording.active) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                if (recording.active) "녹화 정지" else "녹화 시작",
                vm::toggleRecording, enabled = block == null || recording.active, tint = NeuDanger, background = bg
            )
            CircleIconButton(
                if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                if (muted) "음소거 해제" else "음소거", vm::toggleMute, tint = tint, background = bg
            )
            CircleIconButton(Icons.Default.Refresh, "새로고침", vm::refreshStream, tint = tint, background = bg)
            CircleIconButton(
                if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                if (fullscreen) "전체 화면 종료" else "전체 화면", { vm.setFullscreen(!fullscreen) }, tint = tint, background = bg
            )
            CircleIconButton(Icons.Default.MoreHoriz, "화질, 볼륨, 줌", { moreOpen = true }, tint = tint, background = bg)
        }
        if (!overVideo && block != null && stream == StreamState.Connected) {
            VerticalGap(6)
            NoticeBox(block, color = NeuWarning)
        }
    }

    if (moreOpen) MoreControlsSheet(vm, onDismiss = { moreOpen = false })
}

@Composable
private fun MoreControlsSheet(vm: MonitoringViewModel, onDismiss: () -> Unit) {
    val quality by vm.quality.collectAsStateWithLifecycle()
    val zoom by vm.zoom.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val camera by vm.camera.collectAsStateWithLifecycle()

    CleanSheet(onDismiss = onDismiss) {
        Text("화질", color = NeuMuted, style = MaterialTheme.typography.labelLarge)
        VerticalGap(6)
        camera?.let { cam -> ChipRow(vm.qualities(cam).map { it to it.label }, quality, vm::selectQuality) }
        VerticalGap(18)
        Text("볼륨", color = NeuMuted, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = if (muted) 0f else volume,
            onValueChange = vm::setVolume,
            colors = SliderDefaults.colors(thumbColor = NeuAccent, activeTrackColor = NeuAccent, inactiveTrackColor = NeuSurfaceStrong, inactiveTickColor = NeuMuted)
        )
        VerticalGap(10)
        Text("디지털 줌", color = NeuMuted, style = MaterialTheme.typography.labelLarge)
        VerticalGap(8)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleIconButton(Icons.Default.Remove, "축소", { vm.setZoom(ZoomMath.zoomOut(zoom)) }, enabled = zoom.scale > ZoomMath.MIN)
            Text("${"%.1f".format(zoom.scale)}x", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            CircleIconButton(Icons.Default.Add, "확대", { vm.setZoom(ZoomMath.zoomIn(zoom)) }, enabled = zoom.scale < ZoomMath.MAX)
            CircleIconButton(Icons.Default.CenterFocusStrong, "원래대로", { vm.setZoom(ZoomView()) }, enabled = zoom.isZoomed)
        }
    }
}

@Composable
private fun InfoCard(camera: Camera, vm: MonitoringViewModel, nav: NavHostController) {
    val status by vm.deviceStatus.collectAsStateWithLifecycle()
    val zones by vm.zones.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }

    ListGroup {
        ListRow("위치", value = camera.location.ifBlank { Validators.LOCATION_DEFAULT })
        ListRow("마지막 수신", value = KstTime.formatLastSeen(camera.lastSeen))
        ListRow("프라이버시 존", value = "${zones.size}개 (활성 ${zones.count { it.enabled }}개)", onClick = { nav.navigate(PrivacyZoneRoute(camera.mac)) })
        ListRow("상세 정보", divider = false, onClick = { detailOpen = true })
    }

    if (detailOpen) CleanSheet(onDismiss = { detailOpen = false }, title = "상세 정보") {
        InfoRow("이름", camera.name.ifBlank { "-" })
        InfoRow("보드 ID", camera.deviceId)
        InfoRow("시리얼", camera.serial?.ifBlank { "-" } ?: "-")
        InfoRow("MAC", Mac.format(camera.mac))
        InfoRow("VPN IP", camera.vpnIp ?: "-")
        InfoRow("펌웨어", camera.firmware ?: "-")
        InfoRow("업타임", KstTime.formatUptime(status?.uptimeSeconds))
        VerticalGap(14)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuOutlineButton("이름 수정", { detailOpen = false; renaming = true }, Modifier.weight(1f), enabled = vm.canRename)
            NeuOutlineButton("카메라 설정", { detailOpen = false; nav.navigate(CameraSettingsRoute(camera.mac)) }, Modifier.weight(1f))
        }
    }

    if (renaming) RenameDialog(camera, onDismiss = { renaming = false }) {
        vm.rename(it)
        renaming = false
    }
}
