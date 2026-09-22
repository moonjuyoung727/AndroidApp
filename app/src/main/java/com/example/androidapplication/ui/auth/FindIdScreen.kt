package com.example.androidapplication.ui.auth

import androidx.compose.foundation.shape.CircleShape
import com.example.androidapplication.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp




@Composable
fun FindIdScreen (
    onBackClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBg)
            .padding(horizontal = 32.dp)
        ) {
        Spacer(modifier = Modifier.height(30.dp))

        IconButton(
            onClick = onBackClick,
            modifier = Modifier.neuRaised(CircleShape, 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = NeuAccent
            )
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "아이디 찾기",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = NeuAccent
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "가입할 때 사용한 이메일을 입력해 주세요.",
            color = NeuMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {email = it},
            modifier = Modifier.fillMaxWidth()
                .neuInset(NeuFieldShape),
            placeholder = {
                Text(
                    text = "이메일",
                    color = NeuMuted
                )
            },
            singleLine = true,

            colors = neuTextFieldColors(),

            shape = NeuFieldShape
        )

        Spacer(modifier = Modifier.height(30.dp))

        NeuButton(
            onClick = {
                //나중에 아이디 찾기 API 호출
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text(
                text = "아이디 찾기",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

