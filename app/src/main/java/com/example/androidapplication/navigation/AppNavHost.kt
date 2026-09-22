package com.example.androidapplication.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.androidapplication.ui.theme.NeuAccent
import com.example.androidapplication.ui.theme.NeuBg
import com.example.androidapplication.ui.theme.NeuMuted
import com.example.androidapplication.ui.theme.neuBarShadow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

import com.example.androidapplication.model.NotificationItem
import com.example.androidapplication.network.RetrofitClient
import com.example.androidapplication.repository.AuthRepository
import com.example.androidapplication.repository.EventRepository
import com.example.androidapplication.ui.auth.FindIdScreen
import com.example.androidapplication.ui.auth.FindPasswordScreen
import com.example.androidapplication.ui.auth.LoginScreen
import com.example.androidapplication.ui.auth.SignupScreen
import com.example.androidapplication.ui.camera.CameraDetailScreen
import com.example.androidapplication.ui.camera.CameraListScreen
import com.example.androidapplication.ui.events.EventListScreen
import com.example.androidapplication.ui.home.HomeScreen
import com.example.androidapplication.ui.multiview.MultiViewScreen
import com.example.androidapplication.ui.notifications.NotificationBellAction
import com.example.androidapplication.ui.notifications.NotificationsScreen
import com.example.androidapplication.ui.profile.ProfileMenuAction
import com.example.androidapplication.ui.profile.UserSettingsScreen
import com.example.androidapplication.ui.screens.EventDetailScreen
import com.example.androidapplication.ui.settings.SettingsScreen
import com.example.androidapplication.viewmodel.AuthViewModelFactory
import com.example.androidapplication.viewmodel.EventDetailViewModel
import com.example.androidapplication.viewmodel.LoginViewModel
import com.example.androidapplication.viewmodel.SignupViewModel

private val mainRoutes = setOf(
    "home", "multiview", "events", "notifications", "settings", "user-settings"
)

private fun titleOf(route: String?) = when (route) {
    "home" -> "홈"
    "multiview" -> "멀티뷰"
    "events" -> "이벤트"
    "notifications" -> "알림"
    "settings" -> "설정"
    "user-settings" -> "사용자 설정"
    else -> ""
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomItems = listOf(
    BottomItem("home", "홈", Icons.Default.Home),
    BottomItem("multiview", "멀티뷰", Icons.Default.GridView),
    BottomItem("events", "이벤트", Icons.Default.Event),
    BottomItem("notifications", "알림", Icons.Default.Notifications),
    BottomItem("settings", "설정", Icons.Default.Settings)
)

// TODO: 서버 API 로 가져온 최근 알림으로 교체 (임시 예시 데이터)
private val sampleNotifications = listOf(
    NotificationItem(1, "움직임 감지", "현관 카메라에서 움직임이 감지되었습니다.", "방금 전"),
    NotificationItem(2, "카메라 오프라인", "주차장 카메라 연결이 끊어졌습니다.", "10분 전"),
    NotificationItem(3, "움직임 감지", "거실 카메라에서 움직임이 감지되었습니다.", "1시간 전"),
    NotificationItem(4, "카메라 온라인", "주차장 카메라가 다시 연결되었습니다.", "2시간 전"),
    NotificationItem(5, "이벤트 발생", "뒷마당 카메라에서 이벤트가 발생했습니다.", "어제"),
    NotificationItem(6, "움직임 감지", "현관 카메라에서 움직임이 감지되었습니다.", "어제")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {

    val navController = rememberNavController()

    val repository = remember { AuthRepository(RetrofitClient.authApi) }
    val factory = remember { AuthViewModelFactory(repository) }
    val eventFactory = remember {
        viewModelFactory {
            initializer { EventDetailViewModel(EventRepository(RetrofitClient.eventApi)) }
        }
    }

    /* 현재 화면 라우트 관찰 */
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBars = currentRoute in mainRoutes

    Scaffold(
        topBar = {
            if (showBars) {
                TopAppBar(
                    modifier = Modifier.neuBarShadow(bottom = true),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = NeuBg,
                        titleContentColor = NeuAccent,
                        actionIconContentColor = NeuAccent
                    ),
                    title = { Text(titleOf(currentRoute)) },
                    actions = {
                        NotificationBellAction(
                            notifications = sampleNotifications,
                            onViewAllClick = {
                                navController.navigate("notifications") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                        ProfileMenuAction(
                            onUserSettingsClick = {
                                navController.navigate("user-settings") { launchSingleTop = true }
                            }
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (showBars) {
                NavigationBar(
                    modifier = Modifier.neuBarShadow(bottom = false),
                    containerColor = NeuBg,
                    contentColor = NeuAccent
                ) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeuAccent,
                                selectedTextColor = NeuAccent,
                                unselectedIconColor = NeuMuted,
                                unselectedTextColor = NeuMuted,
                                indicatorColor = Color(0xFF23262B)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("login") {
                val loginViewModel: LoginViewModel = viewModel(factory = factory)
                LoginScreen(
                    viewModel = loginViewModel,
                    onLoginSuccess = {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                    onFindIdClick = { navController.navigate("find-id") },
                    onFindPasswordClick = { navController.navigate("find-password") },
                    onSignupClick = { navController.navigate("signup") }
                )
            }
            composable("signup") {

                val signupViewModel: SignupViewModel = viewModel(factory = factory)
                SignupScreen(
                    viewModel = signupViewModel,
                    onSignupSuccess = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable("find-id") {
                FindIdScreen(onBackClick = { navController.popBackStack() })
            }
            composable("find-password") {
                FindPasswordScreen(onBackClick = { navController.popBackStack() })
            }
            /* ==== 메인 화면 (상단/하단바 표시 ==== */
            composable("home") { HomeScreen() }
            composable("multiview") { MultiViewScreen() }
            composable("events") { EventListScreen() }
            composable("settings") { SettingsScreen() }
            composable("user-settings") { UserSettingsScreen() }
            composable("notifications") { NotificationsScreen() }

            /* ==== 상세 화면 (id 를 받아 API 로 조회) ==== */
            composable("cameras") { CameraListScreen() }
            composable(
                route = "cameras/{cameraId}",
                arguments = listOf(navArgument("cameraId") { type = NavType.LongType })
            ) { entry ->
                CameraDetailScreen(cameraId = entry.arguments?.getLong("cameraId") ?: 0L)
            }
            composable(
                route = "events/{eventId}",
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { entry ->
                val eventDetailViewModel: EventDetailViewModel = viewModel(factory = eventFactory)
                EventDetailScreen(
                    eventId = entry.arguments?.getLong("eventId") ?: 0L,
                    viewModel = eventDetailViewModel
                )
            }
        }
    }
}
