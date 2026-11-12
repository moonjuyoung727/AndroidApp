package com.example.androidapplication.navigation

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.EVENT_POLL_MS
import com.example.androidapplication.core.common.LocalPollGate
import com.example.androidapplication.core.common.PollWhileStarted
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.core.session.AuthState
import com.example.androidapplication.feature.auth.AuthScreen
import com.example.androidapplication.feature.dashboard.DashboardScreen
import com.example.androidapplication.feature.devices.DevicesScreen
import com.example.androidapplication.feature.events.EventDetailScreen
import com.example.androidapplication.feature.events.EventsScreen
import com.example.androidapplication.feature.media.MediaDetailScreen
import com.example.androidapplication.feature.media.MediaLibraryScreen
import com.example.androidapplication.feature.monitoring.MonitoringScreen
import com.example.androidapplication.feature.more.MoreScreen
import com.example.androidapplication.feature.multiview.MultiviewScreen
import com.example.androidapplication.feature.settings.*
import com.example.androidapplication.feature.vpn.VpnBanner
import com.example.androidapplication.feature.vpn.VpnCheckScreen
import com.example.androidapplication.feature.vpn.VpnGuideSheet
import com.example.androidapplication.feature.vpn.VpnManageScreen
import kotlinx.coroutines.delay

/** 화면 어디서든 VPN 재연결과 안내를 부른다. */
data class VpnActions(val reconnect: () -> Unit, val showGuide: () -> Unit)

val LocalVpnActions = compositionLocalOf { VpnActions({}, {}) }

/** 전체 화면 영상에서 앱바·탭·배너를 숨긴다. */
class ShellChrome { var hidden by mutableStateOf(false) }

val LocalShellChrome = compositionLocalOf { ShellChrome() }

@Composable
fun AppRoot(vm: ShellViewModel = hiltViewModel()) {
    val auth by vm.auth.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.messages.collect { message ->
                val result = snackbar.showSnackbar(message.text, actionLabel = message.actionLabel, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
            }
        }
    }

    when (val state = auth) {
        AuthState.Loading -> FullScreenMessage(Modifier.background(NeuBg)) { CircularProgressIndicator(color = NeuAccent) }
        AuthState.LoggedOut -> Scaffold(containerColor = NeuBg, snackbarHost = { CleanSnackbarHost(snackbar) }) { padding ->
            AuthScreen(Modifier.padding(padding))
        }
        is AuthState.LoggedIn -> key(state.session.userId) { MainShell(vm, snackbar) }
    }
}

@Composable
private fun CleanSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(data, shape = NeuFieldShape, containerColor = Color(0xFF3A3E46), contentColor = NeuAccent, actionColor = NeuAccent)
    }
}

private data class Tab(val label: String, val icon: ImageVector, val route: Any, val matches: (NavDestination) -> Boolean)

private val TABS = listOf(
    Tab("대시보드", Icons.Default.SpaceDashboard, DashboardRoute) { it.hasRoute<DashboardRoute>() },
    Tab("멀티뷰", Icons.Default.GridView, MultiviewRoute()) { it.hasRoute<MultiviewRoute>() },
    Tab("이벤트", Icons.Default.Event, EventsRoute()) { it.hasRoute<EventsRoute>() },
    Tab("저장 미디어", Icons.Default.PermMedia, MediaRoute) { it.hasRoute<MediaRoute>() },
    Tab("더보기", Icons.Default.Menu, MoreRoute) { it.hasRoute<MoreRoute>() }
)

private fun titleOf(d: NavDestination?): String = when {
    d == null -> ""
    d.hasRoute<DashboardRoute>() -> "대시보드"
    d.hasRoute<MultiviewRoute>() -> "멀티뷰"
    d.hasRoute<EventsRoute>() -> "이벤트"
    d.hasRoute<MediaRoute>() -> "저장 미디어"
    d.hasRoute<MoreRoute>() -> "더보기"
    d.hasRoute<MonitoringRoute>() -> "실시간 모니터링"
    d.hasRoute<EventDetailRoute>() -> "이벤트 상세"
    d.hasRoute<MediaDetailRoute>() -> "파일 상세"
    d.hasRoute<DevicesRoute>() -> "기기 관리"
    d.hasRoute<VpnManageRoute>() -> "VPN 연결 관리"
    d.hasRoute<SettingsRoute>() -> "설정"
    d.hasRoute<GeneralSettingsRoute>() -> "일반"
    d.hasRoute<NotificationSettingsRoute>() -> "알림"
    d.hasRoute<SubscriptionRoute>() -> "구독"
    d.hasRoute<CloudStorageRoute>() -> "클라우드 저장"
    d.hasRoute<LocalStorageRoute>() -> "이 기기 저장"
    d.hasRoute<PrivacyZoneRoute>() -> "프라이버시 존"
    d.hasRoute<CameraSettingsRoute>() -> "카메라 설정"
    d.hasRoute<AccountRoute>() -> "사용자 정보"
    d.hasRoute<LoginSessionsRoute>() -> "로그인 기기"
    else -> ""
}

@Composable
private fun MainShell(vm: ShellViewModel, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val chrome = remember { ShellChrome() }
    val vpn by vm.vpn.collectAsStateWithLifecycle()
    val notifications by vm.notifications.collectAsStateWithLifecycle()
    val session by vm.auth.collectAsStateWithLifecycle()
    val plan by vm.planName.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var bellOpen by rememberSaveable { mutableStateOf(false) }
    var profileOpen by rememberSaveable { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onVpnConsentResult(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) { vm.consentRequests.collect(consent::launch) }

    PollWhileStarted(intervalMs = EVENT_POLL_MS, online = vm.vpnUsable) { vm.refreshNotifications() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.recheckVpnWhileOffline() }
    }
    LaunchedEffect(Unit) { vm.loadSettings() }

    val actions = remember(vm) { VpnActions(reconnect = vm::reconnectVpn, showGuide = { guideOpen = true }) }
    val isVpnCheck = destination?.hasRoute<VpnCheckRoute>() == true
    val isTab = destination != null && TABS.any { it.matches(destination) }
    val showBars = !chrome.hidden && !isVpnCheck && destination != null

    CompositionLocalProvider(LocalVpnActions provides actions, LocalShellChrome provides chrome, LocalPollGate provides vm.vpnUsable) {
        Scaffold(
            containerColor = NeuBg,
            contentWindowInsets = if (chrome.hidden) WindowInsets(0) else WindowInsets.safeDrawing,
            snackbarHost = { CleanSnackbarHost(snackbar) },
            topBar = {
                if (showBars) Column(Modifier.background(NeuBg).statusBarsPadding()) {
                    if (isTab) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding, top = 14.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(titleOf(destination), Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            CircleIconButton(Icons.Default.Notifications, "알림 ${notifications.unread}건", { bellOpen = true }, badge = notifications.unread)
                            CircleIconButton(Icons.Default.Person, "프로필", { profileOpen = true })
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircleIconButton(
                                Icons.AutoMirrored.Filled.ArrowBack, "뒤로",
                                { (activity as? ComponentActivity)?.onBackPressedDispatcher?.onBackPressed() },
                                size = 44.dp
                            )
                            Text(titleOf(destination), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (vpn.showBanner) VpnBanner(onGuide = { guideOpen = true }, onRetry = vm::reconnectVpn, onDismiss = vm::dismissVpnBanner)
                }
            },
            bottomBar = {
                if (showBars && isTab) Column(Modifier.background(NeuBg)) {
                    HorizontalDivider(color = NeuLine)
                    NavigationBar(containerColor = NeuBg, tonalElevation = 0.dp) {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = destination?.let(tab.matches) == true,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, tab.label, modifier = Modifier.size(26.dp)) },
                                alwaysShowLabel = false,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeuAccent,
                                    indicatorColor = Color.Transparent,
                                    unselectedIconColor = NeuMuted.copy(alpha = 0.55f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            AppNavHost(nav, Modifier.padding(if (chrome.hidden) PaddingValues(0.dp) else padding), vm)
        }

        if (guideOpen) VpnGuideSheet(
            status = vpn.status,
            onCheck = vm::reconnectVpn,
            onManage = { guideOpen = false; nav.navigate(VpnManageRoute) { launchSingleTop = true } },
            onDismiss = { guideOpen = false }
        )

        if (bellOpen) CleanSheet(onDismiss = { bellOpen = false }, title = "최근 알림") {
            if (notifications.recent.isEmpty()) {
                Text("새로운 알림이 없습니다.", Modifier.padding(vertical = 24.dp), color = NeuMuted)
            }
            notifications.recent.forEach { item ->
                ListRowPlain(
                    title = "${item.camera} · ${EventTypes.label(item.type)}",
                    subtitle = KstTime.format(item.occurredAt),
                    dot = eventColor(item.type)
                ) {
                    bellOpen = false
                    nav.switchTab(EventsRoute())
                }
            }
        }

        if (profileOpen) CleanSheet(onDismiss = { profileOpen = false }) {
            val current = (session as? AuthState.LoggedIn)?.session
            Text(current?.name?.ifBlank { "-" } ?: "-", style = MaterialTheme.typography.headlineSmall)
            Text(listOfNotNull(current?.email?.ifBlank { null }, plan).joinToString(" · ").ifBlank { "-" }, color = NeuMuted, style = MaterialTheme.typography.bodySmall)
            VerticalGap(16)
            ListGroup {
                ListRow("사용자 정보", onClick = { profileOpen = false; nav.navigate(AccountRoute) { launchSingleTop = true } })
                ListRow("설정", onClick = { profileOpen = false; nav.navigate(SettingsRoute) { launchSingleTop = true } })
                ListRow("로그아웃", titleColor = NeuDanger, divider = false, showChevron = false, onClick = { profileOpen = false; vm.logout() })
            }
        }
    }
}

/** 카드 밖 목록의 한 줄: 색 점, 제목, 보조 문구. */
@Composable
fun ListRowPlain(title: String, subtitle: String, dot: Color, modifier: Modifier = Modifier, trailing: String? = null, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = NeuMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (trailing != null) Text(trailing, color = NeuMuted, style = MaterialTheme.typography.labelMedium)
    }
}

fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, modifier: Modifier, shell: ShellViewModel) {
    NavHost(nav, startDestination = VpnCheckRoute, modifier = modifier.fillMaxSize()) {
        composable<VpnCheckRoute> {
            VpnCheckScreen(
                onConnected = { nav.navigate(DashboardRoute) { popUpTo(VpnCheckRoute) { inclusive = true } } },
                onLogout = shell::logout
            )
        }
        composable<DashboardRoute> { DashboardScreen(nav) }
        composable<MultiviewRoute> { MultiviewScreen(nav, it.toRoute<MultiviewRoute>().status) }
        composable<EventsRoute> { EventsScreen(nav) }
        composable<MediaRoute> { MediaLibraryScreen(nav) }
        composable<MoreRoute> { MoreScreen(nav, onLogout = shell::logout) }
        composable<MonitoringRoute> { MonitoringScreen(nav) }
        composable<EventDetailRoute> { EventDetailScreen(nav) }
        composable<MediaDetailRoute> { MediaDetailScreen(nav) }
        composable<DevicesRoute> { DevicesScreen(nav) }
        composable<VpnManageRoute> { VpnManageScreen() }
        composable<SettingsRoute> { SettingsScreen(nav) }
        composable<GeneralSettingsRoute> { GeneralSettingsScreen() }
        composable<NotificationSettingsRoute> { NotificationSettingsScreen() }
        composable<SubscriptionRoute> { SubscriptionScreen() }
        composable<CloudStorageRoute> { CloudStorageScreen() }
        composable<LocalStorageRoute> { LocalStorageScreen() }
        composable<PrivacyZoneRoute> { PrivacyZoneScreen(nav) }
        composable<CameraSettingsRoute> { CameraSettingsScreen(nav) }
        composable<AccountRoute> { AccountScreen(nav, onLogout = shell::logout) }
        composable<LoginSessionsRoute> { LoginSessionsScreen() }
    }
}

/** 1초마다 갱신되는 현재 시각 (KST). */
@Composable
fun rememberKstClock(): String {
    var now by remember { mutableStateOf(KstTime.format(java.time.Instant.now())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = KstTime.format(java.time.Instant.now())
            delay(1_000)
        }
    }
    return now
}
