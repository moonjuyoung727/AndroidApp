package com.example.androidapplication.ui.profile

import com.example.androidapplication.ui.theme.NeuBg
import com.example.androidapplication.ui.theme.NeuCardShape

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 상단바 프로필 아이콘 + 누르면 뜨는 계정 요약 팝업
@Composable
fun ProfileMenuAction(
    onUserSettingsClick: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.AccountCircle, contentDescription = "프로필")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp),
            containerColor = NeuBg,
            shape = NeuCardShape,
            shadowElevation = 16.dp,
            tonalElevation = 0.dp
        ) {
            // TODO: API 로 가져온 계정 정보로 교체
            ProfileInfoRow(label = "이름", value = "-")
            ProfileInfoRow(label = "아이디", value = "-")
            ProfileInfoRow(label = "이메일", value = "-")

            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("사용자 설정") },
                onClick = {
                    expanded = false
                    onUserSettingsClick()
                }
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
