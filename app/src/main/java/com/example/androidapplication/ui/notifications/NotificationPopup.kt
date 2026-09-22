package com.example.androidapplication.ui.notifications

import com.example.androidapplication.ui.theme.NeuBg
import com.example.androidapplication.ui.theme.NeuCardShape

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.androidapplication.model.NotificationItem

private const val MAX_POPUP_ITEMS = 5

// 상단바 알림 아이콘 + 누르면 뜨는 최근 알림 팝업 (최대 5개)
@Composable
fun NotificationBellAction(
    notifications: List<NotificationItem>,
    onViewAllClick: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Notifications, contentDescription = "최근 알림")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 260.dp, max = 320.dp),
            containerColor = NeuBg,
            shape = NeuCardShape,
            shadowElevation = 16.dp,
            tonalElevation = 0.dp
        ) {
            val recent = notifications.take(MAX_POPUP_ITEMS)

            if (recent.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("새 알림이 없습니다") },
                    onClick = {},
                    enabled = false
                )
            } else {
                recent.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.time,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        onClick = { expanded = false }
                    )
                }
            }

            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("전체 알림 보기") },
                onClick = {
                    expanded = false
                    onViewAllClick()
                }
            )
        }
    }
}
