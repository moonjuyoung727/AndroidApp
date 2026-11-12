package com.example.androidapplication.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.designsystem.*

@Composable
fun AuthScreen(modifier: Modifier = Modifier, vm: AuthViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val notice by vm.loginNotice.collectAsStateWithLifecycle()
    val remembered by vm.remembered.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }
    // 비밀번호는 프로세스 재생성 시 복원하지 않는다
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    LaunchedEffect(remembered) {
        remembered?.let { (saved, checked) ->
            if (email.isEmpty()) email = saved
            rememberMe = checked
        }
    }

    fun switch(target: AuthMode) {
        mode = target
        password = ""
        confirm = ""
        code = ""
        vm.clearFeedback()
    }

    LaunchedEffect(ui.completedMode) {
        if (ui.completedMode != null) {
            mode = AuthMode.Login
            password = ""
            confirm = ""
            code = ""
            vm.clearCompleted()
        }
    }

    val errors = ui.fieldErrors
    fun submit() = when (mode) {
        AuthMode.Login -> vm.login(email, password, rememberMe)
        AuthMode.Signup -> vm.signup(name, email, password, confirm)
        AuthMode.FindId -> vm.findId(name, phone, code)
        AuthMode.FindPassword -> vm.resetPassword(email, code, password, confirm)
    }

    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("SECURE CAM", color = NeuMuted, style = MaterialTheme.typography.labelLarge)
        VerticalGap(6)
        Text(mode.title, style = MaterialTheme.typography.headlineLarge)
        VerticalGap(28)

        val gap = 10
        if (mode == AuthMode.Signup || mode == AuthMode.FindId) {
            NeuTextField(name, { name = it.take(Validators.NAME_MAX) }, "이름", error = errors[AuthViewModel.FIELD_NAME])
            VerticalGap(gap)
        }
        if (mode != AuthMode.FindId) {
            NeuTextField(email, { email = it.take(Validators.EMAIL_MAX) }, "이메일", error = errors[AuthViewModel.FIELD_EMAIL], keyboardType = KeyboardType.Email)
            VerticalGap(gap)
        }
        if (mode == AuthMode.FindId) {
            NeuTextField(phone, { phone = it.filter(Char::isDigit).take(11) }, "휴대폰 번호", error = errors[AuthViewModel.FIELD_PHONE], keyboardType = KeyboardType.Phone)
            VerticalGap(gap)
        }
        if (mode == AuthMode.FindId || mode == AuthMode.FindPassword) {
            NeuTextField(code, { code = it.take(20) }, "인증 코드", error = errors[AuthViewModel.FIELD_CODE], keyboardType = KeyboardType.Number)
            VerticalGap(gap)
        }
        if (mode != AuthMode.FindId) {
            PasswordField(
                password,
                { password = if (mode == AuthMode.Login) it.take(Validators.PASSWORD_MAX) else Validators.filterPasswordInput(it) },
                if (mode == AuthMode.FindPassword) "새 비밀번호" else "비밀번호",
                errors[AuthViewModel.FIELD_PASSWORD]
            )
            VerticalGap(gap)
        }
        if (mode == AuthMode.Signup || mode == AuthMode.FindPassword) {
            PasswordField(confirm, { confirm = Validators.filterPasswordInput(it) }, "비밀번호 확인", errors[AuthViewModel.FIELD_CONFIRM])
            VerticalGap(gap)
        }
        if (mode == AuthMode.Login) {
            Row(Modifier.fillMaxWidth().clickable { rememberMe = !rememberMe }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMe, onCheckedChange = { rememberMe = it },
                    colors = CheckboxDefaults.colors(checkedColor = NeuAccent, checkmarkColor = NeuBg, uncheckedColor = NeuMuted)
                )
                Text("로그인 상태 유지", color = NeuMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        val message = ui.generalError ?: notice.takeIf { mode == AuthMode.Login }
        if (message != null) {
            VerticalGap(4)
            NoticeBox(message, color = NeuDanger)
        }
        ui.info?.let {
            VerticalGap(4)
            NoticeBox(it, color = NeuSuccess)
        }
        VerticalGap(16)
        PrimaryButton(if (ui.busy) "처리 중..." else mode.title, ::submit, Modifier.fillMaxWidth(), enabled = !ui.busy)
        VerticalGap(16)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AuthMode.entries.filter { it != mode }.forEach { target -> TextAction(target.title, { switch(target) }, color = NeuMuted) }
        }
    }
}

/** 눈 버튼은 누르고 있는 동안만 비밀번호를 표시한다. */
@Composable
fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, error: String?, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    NeuTextField(
        value, onValueChange, label, modifier,
        error = error,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (pressed) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = {}, interactionSource = source) {
                Icon(if (pressed) Icons.Default.Visibility else Icons.Default.VisibilityOff, "누르고 있으면 비밀번호 표시", tint = NeuMuted, modifier = Modifier.size(20.dp))
            }
        }
    )
}
