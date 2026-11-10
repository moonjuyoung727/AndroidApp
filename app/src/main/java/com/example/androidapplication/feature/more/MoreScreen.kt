package com.example.androidapplication.feature.more

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.androidapplication.core.designsystem.*
import com.example.androidapplication.navigation.AccountRoute
import com.example.androidapplication.navigation.DevicesRoute
import com.example.androidapplication.navigation.SettingsRoute
import com.example.androidapplication.navigation.VpnManageRoute

@Composable
fun MoreScreen(nav: NavHostController, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ListGroup {
            ListRow("기기 관리", icon = Icons.Default.Videocam, onClick = { nav.navigate(DevicesRoute) })
            ListRow("VPN 연결 관리", icon = Icons.Default.VpnKey, onClick = { nav.navigate(VpnManageRoute) })
            ListRow("설정", icon = Icons.Default.Settings, onClick = { nav.navigate(SettingsRoute) })
            ListRow("사용자 정보", icon = Icons.Default.Person, divider = false, onClick = { nav.navigate(AccountRoute) })
        }
        ListGroup {
            ListRow("로그아웃", icon = Icons.AutoMirrored.Filled.ExitToApp, titleColor = NeuDanger, divider = false, showChevron = false, onClick = onLogout)
        }
    }
}
