package com.example.androidapplication.feature.media

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn as AndroidxOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.formatBytes
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.media.MediaEntity
import com.example.androidapplication.core.media.MediaType
import com.example.androidapplication.navigation.DevicesRoute
import com.example.androidapplication.navigation.LocalShellChrome
import com.example.androidapplication.navigation.MediaDetailRoute
import java.io.File
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaLibraryScreen(nav: NavHostController, vm: MediaLibraryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val all by vm.allItems.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.exportSelected()
    }

    BackHandler(enabled = selected.isNotEmpty()) { vm.clearSelection() }

    val selecting = selected.isNotEmpty()
    val groups = remember(items) { items.groupBy { KstTime.date(Instant.ofEpochMilli(it.capturedAtUtc)) } }
    val filterActive = filters.mac != null || filters.daysBack != null || filters.origin != null || !filters.newestFirst

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (selecting) {
                DimIconButton(Icons.Default.Close, "선택 해제", vm::clearSelection)
                Text("${selected.size}개 선택", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                CircleIconButton(Icons.Default.Share, "공유", { vm.shareIntent()?.let(context::startActivity) }, size = 44.dp)
                CircleIconButton(Icons.Default.SaveAlt, "갤러리로 내보내기", {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.exportSelected()
                }, size = 44.dp)
                CircleIconButton(Icons.Default.Delete, "삭제", { confirmDelete = true }, size = 44.dp, tint = NeuDanger)
            } else {
                ChipRow(listOf<Pair<MediaType?, String>>(null to "전체", MediaType.IMAGE to "사진", MediaType.VIDEO to "동영상"), filters.type, { t -> vm.setFilters { it.copy(type = t) } }, Modifier.weight(1f))
                CircleIconButton(Icons.Default.Tune, "필터", { filterOpen = true }, size = 44.dp, badge = if (filterActive) 1 else 0)
            }
        }

        if (items.isEmpty()) {
            EmptyState(
                Icons.Default.PermMedia,
                if (all.isEmpty()) "저장된 파일이 없습니다." else "조건에 맞는 파일이 없습니다.",
                if (all.isEmpty()) "모니터링 화면에서 스냅샷을 찍거나 녹화해 보세요." else null,
                Modifier.weight(1f)
            ) {
                if (all.isEmpty()) NeuOutlineButton("모니터링으로 이동", { nav.navigate(DevicesRoute) })
            }
        } else {
            LazyVerticalGrid(
                GridCells.Adaptive(104.dp), Modifier.weight(1f),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groups.forEach { (date, entries) ->
                    item(key = "h$date", span = { GridItemSpan(maxLineSpan) }) {
                        Text(date, Modifier.padding(top = 10.dp, bottom = 4.dp), color = NeuMuted, style = MaterialTheme.typography.labelLarge)
                    }
                    items(entries, key = { it.id }) { entity ->
                        val isSelected = entity.id in selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(NeuSmallShape)
                                .background(Color.Black)
                                .then(if (isSelected) Modifier.border(2.dp, NeuAccent, NeuSmallShape) else Modifier)
                                .combinedClickable(
                                    onClick = { if (selecting) vm.toggle(entity.id) else nav.navigate(MediaDetailRoute(entity.id)) },
                                    onLongClick = { vm.toggle(entity.id) },
                                    onLongClickLabel = "선택"
                                )
                        ) {
                            AsyncImage(entity.thumbnailPath?.let(::File) ?: entity.filePath.takeIf { entity.type == MediaType.IMAGE }?.let(::File), entity.fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (entity.type == MediaType.VIDEO) {
                                Text(KstTime.formatElapsed(entity.durationMs), Modifier.align(Alignment.BottomStart).padding(6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            if (isSelected) Icon(Icons.Default.CheckCircle, "선택됨", tint = NeuAccent, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (filterOpen) CleanSheet(onDismiss = { filterOpen = false }, title = "필터") {
        val cameras = all.distinctBy { it.mac }.map { it.mac to it.cameraName }
        ListGroup {
            SettingSelect("카메라", null, listOf<Pair<String?, String>>(null to "전체") + cameras, filters.mac, { m -> vm.setFilters { it.copy(mac = m) } })
            SettingSelect(
                "기간", null,
                listOf<Pair<Int?, String>>(null to "전체", 0 to "오늘", 6 to "최근 7일", 29 to "최근 30일"),
                filters.daysBack, { d -> vm.setFilters { it.copy(daysBack = d) } }
            )
            SettingSelect(
                "이벤트", null,
                listOf<Pair<String?, String>>(null to "전체", ORIGIN_MANUAL to "직접 저장") + EventTypes.labels.map { (k, v) -> k to v },
                filters.origin, { o -> vm.setFilters { it.copy(origin = o) } }
            )
            SettingSelect("정렬", null, listOf(true to "최신순", false to "오래된순"), filters.newestFirst, { n -> vm.setFilters { it.copy(newestFirst = n) } })
        }
        VerticalGap(10)
        NoticeBox("저장된 파일 전체 ${formatBytes(all.sumOf { it.sizeBytes })}")
    }

    if (confirmDelete) ConfirmDialog(
        "파일 삭제", "선택한 ${selected.size}개 파일을 삭제할까요? 삭제하면 되돌릴 수 없습니다.", "삭제",
        onConfirm = { confirmDelete = false; vm.deleteSelected() },
        onDismiss = { confirmDelete = false },
        danger = true
    )
}

@Composable
fun MediaDetailScreen(nav: NavHostController, vm: MediaDetailViewModel = hiltViewModel()) {
    val entity by vm.item.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) entity?.let(vm::export)
    }

    val item = entity ?: run {
        FullScreenMessage { Text("파일을 찾을 수 없습니다.", color = NeuMuted) }
        return
    }

    val chrome = LocalShellChrome.current
    if (fullscreen) {
        ImmersiveLandscape()
        DisposableEffect(Unit) {
            chrome.hidden = true
            onDispose { chrome.hidden = false }
        }
        BackHandler { fullscreen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            MediaContent(item, Modifier.fillMaxSize(), onFullscreen = { fullscreen = false })
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(NeuCardShape).background(Color.Black)) {
            MediaContent(item, Modifier.fillMaxSize(), onFullscreen = { fullscreen = true })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(item.cameraName.ifBlank { Mac.format(item.mac) }, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                Text(KstTime.format(Instant.ofEpochMilli(item.capturedAtUtc)), color = NeuMuted, style = MaterialTheme.typography.bodySmall)
            }
            CircleIconButton(Icons.Default.Share, "공유", { context.startActivity(vm.shareIntent(item)) }, size = 44.dp)
            CircleIconButton(Icons.Default.SaveAlt, "갤러리로 내보내기", {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.export(item)
            }, size = 44.dp)
            CircleIconButton(Icons.Default.Delete, "삭제", { confirmDelete = true }, size = 44.dp, tint = NeuDanger)
        }
        NeuCard(Modifier.fillMaxWidth()) {
            InfoRow("파일명", item.fileName)
            InfoRow("형식", item.format)
            InfoRow("크기", formatBytes(item.sizeBytes))
            InfoRow("해상도", if (item.width > 0) "${item.width} × ${item.height}" else "-")
            if (item.type == MediaType.VIDEO) InfoRow("길이", KstTime.formatElapsed(item.durationMs))
            InfoRow("위치", item.location.ifBlank { "-" })
            InfoRow("프라이버시 마스크", if (item.maskedZoneCount > 0) "적용됨 (${item.maskedZoneCount}개)" else "적용 영역 없음")
            item.eventType?.let { InfoRow("이벤트", EventTypes.label(it)) }
        }
    }

    if (confirmDelete) ConfirmDialog(
        "파일 삭제", "이 파일을 삭제할까요? 삭제하면 되돌릴 수 없습니다.", "삭제",
        onConfirm = { confirmDelete = false; vm.delete(item) { nav.popBackStack() } },
        onDismiss = { confirmDelete = false },
        danger = true
    )
}

@AndroidxOptIn(UnstableApi::class)
@Composable
private fun MediaContent(item: MediaEntity, modifier: Modifier, onFullscreen: () -> Unit) {
    if (item.type == MediaType.VIDEO) {
        VideoPlayer(Uri.fromFile(File(item.filePath)).toString(), null, modifier, onFullscreen)
    } else {
        var zoom by remember { mutableStateOf(ZoomView()) }
        val current by rememberUpdatedState(zoom)
        Box(modifier.clipToBounds().zoomGestures(view = { current }, onChange = { zoom = it })) {
            AsyncImage(File(item.filePath), item.fileName, Modifier.fillMaxSize().zoomLayer(zoom), contentScale = ContentScale.Fit)
            DimIconButton(Icons.Default.Fullscreen, "전체 화면", onFullscreen, Modifier.align(Alignment.TopEnd).padding(4.dp), tint = Color.White)
        }
    }
}

/** APP-F-043: 재생, 배속, 음량, 전체화면. */
@AndroidxOptIn(UnstableApi::class)
@Composable
fun VideoPlayer(uri: String, dataSourceFactory: DataSource.Factory?, modifier: Modifier = Modifier, onFullscreen: (() -> Unit)? = null) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context)
            .apply { if (dataSourceFactory != null) setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)) }
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleStartEffect(player) { onStopOrDispose { player.pause() } }
    var speed by rememberSaveable { mutableStateOf(1f) }
    var volume by rememberSaveable { mutableStateOf(1f) }

    Column(modifier) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    if (onFullscreen != null) setFullscreenButtonClickListener { onFullscreen() }
                }
            },
            update = { it.player = player }
        )
        Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction("${speed}x", {
                val options = listOf(0.5f, 1f, 1.5f, 2f)
                speed = options[(options.indexOf(speed) + 1) % options.size]
                player.playbackParameters = PlaybackParameters(speed)
            }, color = Color.White)
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "음량", tint = NeuMuted, modifier = Modifier.padding(horizontal = 8.dp).size(16.dp))
            Slider(volume, { volume = it; player.volume = it }, Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = NeuAccent, activeTrackColor = NeuAccent, inactiveTrackColor = NeuSurfaceStrong))
        }
    }
}
