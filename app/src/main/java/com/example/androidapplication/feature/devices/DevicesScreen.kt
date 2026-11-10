package com.example.androidapplication.feature.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.androidapplication.core.common.CAMERA_POLL_MS
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.navigation.MonitoringRoute

@Composable
fun DevicesScreen(nav: NavHostController, vm: DevicesViewModel = hiltViewModel()) {
    val state by vm.cameras.collectAsStateWithLifecycle()
    val general by vm.general.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var detailMac by rememberSaveable { mutableStateOf<String?>(null) }
    var renameMac by rememberSaveable { mutableStateOf<String?>(null) }

    PollWhileStarted(CAMERA_POLL_MS, repeat = general.autoRefresh, tick = vm::poll)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    BigNumber("${state.items.size}", "대", style = MaterialTheme.typography.headlineLarge)
                    DataFreshness(state.source, state.fetchedAt)
                }
                CircleIconButton(Icons.Default.Refresh, "새로고침", vm::refresh)
                CircleIconButton(Icons.Default.Add, "카메라 등록", { vm.clearRegisterError(); sheetOpen = true })
            }
        }
        if (state.items.isEmpty()) {
            item {
                EmptyState(
                    Icons.Default.Videocam,
                    if (state.isFetching) "불러오는 중..." else "등록된 카메라가 없습니다.",
                    "카메라를 등록하거나 VPN과 서버 연결을 확인해 주세요."
                )
            }
        } else item {
            ListGroup {
                state.items.forEachIndexed { index, camera ->
                    ListRow(
                        title = camera.name.ifBlank { camera.deviceId },
                        subtitle = "${camera.location.ifBlank { Validators.LOCATION_DEFAULT }} · ${KstTime.formatLastSeen(camera.lastSeen)}",
                        divider = index != state.items.lastIndex,
                        onClick = { nav.navigate(MonitoringRoute(camera.mac)) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(if (camera.isOnline) "온라인" else "오프라인", if (camera.isOnline) NeuSuccess else NeuMuted)
                                DimIconButton(Icons.Default.Tune, "${camera.name} 관리", { detailMac = camera.mac })
                            }
                        }
                    )
                }
            }
        }
    }

    state.items.firstOrNull { it.mac == detailMac }?.let { camera ->
        CleanSheet(onDismiss = { detailMac = null }, title = camera.name.ifBlank { camera.deviceId }) {
            InfoRow("MAC", Mac.format(camera.mac))
            InfoRow("위치", camera.location.ifBlank { Validators.LOCATION_DEFAULT })
            InfoRow("마지막 수신", KstTime.formatLastSeen(camera.lastSeen))
            VerticalGap(14)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeuOutlineButton("이름 수정", { detailMac = null; renameMac = camera.mac }, Modifier.weight(1f), enabled = vm.canUpdate)
                // 카메라 삭제 API가 확정되기 전까지 비활성
                NeuOutlineButton("삭제", {}, Modifier.weight(1f), enabled = false, color = NeuDanger)
            }
            if (!vm.canUpdate) {
                VerticalGap(10)
                NoticeBox("이름 수정과 삭제는 서버 연동 후 사용할 수 있습니다.")
            }
        }
    }

    if (sheetOpen) CleanSheet(onDismiss = { sheetOpen = false }, title = "카메라 등록") {
        RegisterForm(vm, state.items) { sheetOpen = false }
    }

    state.items.firstOrNull { it.mac == renameMac }?.let { camera ->
        RenameDialog(camera, onDismiss = { renameMac = null }) {
            vm.rename(camera.mac, it)
            renameMac = null
        }
    }
}

@Composable
private fun RegisterForm(vm: DevicesViewModel, existing: List<Camera>, onDone: () -> Unit) {
    val busy by vm.registering.collectAsStateWithLifecycle()
    val serverError by vm.registerError.collectAsStateWithLifecycle()
    var mac by remember { mutableStateOf(TextFieldValue("")) }
    var name by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }

    Column(Modifier.fillMaxWidth().imePadding()) {
        TextField(
            value = mac,
            onValueChange = {
                val formatted = Mac.formatInput(it.text)
                mac = TextFieldValue(formatted, TextRange(formatted.length))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("MAC 주소") },
            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            singleLine = true,
            isError = errors["mac"] != null,
            shape = NeuFieldShape,
            colors = cleanFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )
        errors["mac"]?.let { Text(it, color = NeuDanger, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(4.dp)) }
        VerticalGap(10)
        NeuTextField(name, { name = it.take(Validators.CAMERA_NAME_MAX) }, "이름", error = errors["name"], counter = "${name.length}/${Validators.CAMERA_NAME_MAX}")
        VerticalGap(10)
        NeuTextField(
            location, { location = it.take(Validators.CAMERA_LOCATION_MAX) }, "위치 (선택)",
            error = errors["location"], counter = "${location.length}/${Validators.CAMERA_LOCATION_MAX}"
        )
        serverError?.let {
            VerticalGap(8)
            NoticeBox(it, color = NeuDanger)
        }
        VerticalGap(16)
        PrimaryButton(
            if (busy) "등록 중..." else "등록",
            {
                errors = buildMap {
                    Validators.cameraMac(mac.text, existing.map { it.mac })?.let { put("mac", it) }
                    Validators.cameraName(name)?.let { put("name", it) }
                    Validators.cameraLocation(location)?.let { put("location", it) }
                }
                if (errors.isEmpty()) vm.register(mac.text, name, location, onDone)
            },
            Modifier.fillMaxWidth(),
            enabled = !busy
        )
    }
}

@Composable
fun RenameDialog(camera: Camera, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable(camera.mac) { mutableStateOf(camera.name) }
    val error = Validators.cameraName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NeuBg,
        shape = NeuCardShape,
        title = { Text("이름 수정", style = MaterialTheme.typography.titleLarge) },
        text = { NeuTextField(name, { name = it.take(Validators.CAMERA_NAME_MAX) }, "카메라 이름", error = error, counter = "${name.length}/${Validators.CAMERA_NAME_MAX}") },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = error == null) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", color = NeuMuted) } }
    )
}
