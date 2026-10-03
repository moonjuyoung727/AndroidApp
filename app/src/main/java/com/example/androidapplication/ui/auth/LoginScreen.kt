package com.example.androidapplication.ui.auth

import com.example.androidapplication.ui.theme.*

import com.example.androidapplication.viewmodel.LoginViewModel
import com.example.androidapplication.viewmodel.SignupViewModel

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


// 한 카드 안에서 로그인 / 회원가입 / 아이디 찾기 / 비밀번호 찾기를 전환한다
private enum class AuthMode { Login, Signup, FindId, FindPassword }

private data class AuthMessage(val text: String, val isError: Boolean)

private val BadgeSize = 84.dp

// 카드 아래로 빠져나온 서랍 모양 버튼: 보이는 높이 + 카드에 가려지는 윗부분
private val DrawerHeight = 52.dp
private val DrawerOverlap = 22.dp
private val DrawerShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

//아이디 영문 대소문자 + 숫자만 허용
private val IdRegex = Regex("^[A-Za-z0-9]*$")

//비밀번호 영문 대소문자 +  숫자 + 특수문자
private val PasswordRegex = Regex("^[A-Za-z0-9!@#\$%^&*]*$")


// 로그인 화면 생성

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    signupViewModel: SignupViewModel,
    onLoginSuccess: () -> Unit = {}
) {

    var mode by rememberSaveable { mutableStateOf(AuthMode.Login) }

    var userId by rememberSaveable { mutableStateOf(viewModel.savedUserId) }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    // 로그인 상태 유지 (remember me)
    var rememberMe by rememberSaveable { mutableStateOf(viewModel.savedRememberMe) }

    // 화면에서 직접 띄우는 안내 / 오류 메시지
    var message by remember { mutableStateOf<AuthMessage?>(null) }

    val userIdFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val passwordConfirmFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    val findUserIdFocusRequester = remember { FocusRequester() }
    val findEmailFocusRequester = remember { FocusRequester() }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel.loginSuccess) {
        if (viewModel.loginSuccess) {
            onLoginSuccess()
            viewModel.consumeLoginSuccess()
        }
    }

    // 회원가입이 끝나면 카드를 로그인으로 접고 아이디는 남겨 둔다
    LaunchedEffect(signupViewModel.signupSuccess) {
        if (signupViewModel.signupSuccess) {
            signupViewModel.consumeSignupSuccess()
            signupViewModel.clearMessage()
            signupViewModel.resetIdCheck()

            password = ""
            passwordConfirm = ""
            email = ""
            mode = AuthMode.Login

            message = AuthMessage("회원가입이 완료되었습니다. 로그인해 주세요.", isError = false)
        }
    }

    fun switchMode(target: AuthMode) {
        focusManager.clearFocus()

        if (mode == AuthMode.Signup) {
            signupViewModel.resetIdCheck()
            passwordConfirm = ""
        }

        message = null
        viewModel.clearMessage()
        signupViewModel.clearMessage()

        mode = target
    }

    // 뒤로가기를 누르면 앱을 닫지 않고 로그인 카드로 돌아온다
    BackHandler(enabled = mode != AuthMode.Login) {
        switchMode(AuthMode.Login)
    }

    fun showError(text: String, focusRequester: FocusRequester) {
        message = AuthMessage(text, isError = true)
        focusRequester.requestFocus()
    }

    fun isValidEmail(value: String) =
        Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

    fun validateAndLogin() {

        // 아이디 확인
        if (userId.isBlank()) {
            showError("아이디를 입력해 주세요.", userIdFocusRequester)
            return
        }

        //비밀번호 확인
        if (password.isBlank()) {
            showError("비밀번호를 입력해 주세요.", passwordFocusRequester)
            return
        }

        message = null

        viewModel.login(
            id = userId.trim(),
            password = password,
            rememberMe = rememberMe
        )
    }

    fun validateAndSignup() {

        //아이디 입력 여부
        if (userId.isBlank()) {
            showError("아이디를 입력해 주세요.", userIdFocusRequester)
            return
        }

        //아이디 길이
        if (userId.length !in 4..20) {
            showError("아이디는 4~20자로 입력해 주세요.", userIdFocusRequester)
            return
        }

        //아이디 문자 검사
        if (!userId.matches(IdRegex)) {
            showError("아이디는 영문과 숫자만 사용할 수 있습니다.", userIdFocusRequester)
            return
        }

        //중복 확인 여부
        if (!signupViewModel.isIdChecked) {
            showError("아이디 중복 확인을 해주세요.", userIdFocusRequester)
            return
        }

        if (!signupViewModel.isIdAvailable) {
            showError("사용할 수 없는 아이디입니다.", userIdFocusRequester)
            return
        }

        //비밀번호
        if (password.isBlank()) {
            showError("비밀번호를 입력해 주세요.", passwordFocusRequester)
            return
        }

        //비밀번호 길이
        if (password.length !in 8..64) {
            showError("비밀번호는 8~64자로 입력해 주세요.", passwordFocusRequester)
            return
        }

        //비밀번호 허용 문자
        if (!password.matches(PasswordRegex)) {
            showError(
                "비밀번호는 영문, 숫자, ! @ # $ % ^ & * 만 사용할 수 있습니다.",
                passwordFocusRequester
            )
            return
        }

        //비밀번호 확인
        if (passwordConfirm.isBlank()) {
            showError("비밀번호 확인을 입력해 주세요.", passwordConfirmFocusRequester)
            return
        }

        //비밀번호 일치 검사
        if (password != passwordConfirm) {
            showError("비밀번호가 일치하지 않습니다.", passwordConfirmFocusRequester)
            return
        }

        //이메일
        if (email.isBlank()) {
            showError("이메일을 입력해 주세요.", emailFocusRequester)
            return
        }

        //이메일 형식
        if (!isValidEmail(email)) {
            showError("올바른 이메일 형식을 입력해 주세요.", emailFocusRequester)
            return
        }

        message = null

        signupViewModel.signup(
            id = userId.trim(),
            password = password,
            email = email.trim()
        )
    }

    fun validateAndFindId() {
        if (email.isBlank()) {
            showError("이메일을 입력해 주세요.", findEmailFocusRequester)
            return
        }

        if (!isValidEmail(email)) {
            showError("올바른 이메일 형식을 입력해 주세요.", findEmailFocusRequester)
            return
        }

        // TODO: 아이디 찾기 API 연동 (백엔드 명세 확정 후)
        message = AuthMessage("아이디 찾기는 서버 연동 후 사용할 수 있습니다.", isError = false)
    }

    fun validateAndFindPassword() {
        if (userId.isBlank()) {
            showError("아이디를 입력해 주세요.", findUserIdFocusRequester)
            return
        }

        if (email.isBlank()) {
            showError("이메일을 입력해 주세요.", findEmailFocusRequester)
            return
        }

        if (!isValidEmail(email)) {
            showError("올바른 이메일 형식을 입력해 주세요.", findEmailFocusRequester)
            return
        }

        // TODO: 비밀번호 찾기 API 연동 (백엔드 명세 확정 후)
        message = AuthMessage("비밀번호 찾기는 서버 연동 후 사용할 수 있습니다.", isError = false)
    }

    fun submit() {
        when (mode) {
            AuthMode.Login -> validateAndLogin()
            AuthMode.Signup -> validateAndSignup()
            AuthMode.FindId -> validateAndFindId()
            AuthMode.FindPassword -> validateAndFindPassword()
        }
    }

    val isBusy = when (mode) {
        AuthMode.Login -> viewModel.isLoading
        AuthMode.Signup -> signupViewModel.isSigningUp
        else -> false
    }

    // 화면 메시지가 우선, 없으면 서버 응답 메시지
    val shownMessage = message ?: when {
        mode == AuthMode.Login && viewModel.loginMessage.isNotBlank() ->
            AuthMessage(viewModel.loginMessage, viewModel.isLoginError)

        mode == AuthMode.Signup && signupViewModel.signupMessage.isNotBlank() ->
            AuthMessage(signupViewModel.signupMessage, isError = true)

        else -> null
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBg)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Box(modifier = Modifier.fillMaxWidth()) {

                /* 카드 밑에서 빠져나온 버튼 (윗부분은 카드에 가려진다) */
                NeuButton(
                    onClick = { submit() },
                    enabled = !isBusy,
                    shape = DrawerShape,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = DrawerOverlap
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.72f)
                        .height(DrawerOverlap + DrawerHeight)
                ) {
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                        },
                        label = "submitLabel"
                    ) { target ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = NeuBg
                                )

                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Text(
                                text = submitLabelOf(target),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                /* 카드 */
                Column(
                    modifier = Modifier
                        .padding(top = BadgeSize / 2, bottom = DrawerHeight)
                        .fillMaxWidth()
                        .neuRaised(NeuCardShape, 8.dp)
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = BadgeSize / 2 + 14.dp,
                            bottom = 24.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                        },
                        label = "title"
                    ) { target ->
                        Text(
                            text = titleOf(target),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeuAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    /* 입력 폼: 로그인/회원가입은 같은 폼을 늘리고, 찾기는 폼을 바꾼다 */
                    AnimatedContent(
                        targetState = formOf(mode),
                        transitionSpec = {
                            fadeIn(tween(220, delayMillis = 90)) togetherWith
                                    fadeOut(tween(90))
                        },
                        label = "form"
                    ) { form ->
                        when (form) {
                            AuthMode.FindId -> FindIdForm(
                                email = email,
                                onEmailChange = {
                                    email = it
                                    message = null
                                },
                                emailFocusRequester = findEmailFocusRequester,
                                onDone = { submit() }
                            )

                            AuthMode.FindPassword -> FindPasswordForm(
                                userId = userId,
                                onUserIdChange = {
                                    userId = it
                                    message = null
                                },
                                email = email,
                                onEmailChange = {
                                    email = it
                                    message = null
                                },
                                userIdFocusRequester = findUserIdFocusRequester,
                                emailFocusRequester = findEmailFocusRequester,
                                onDone = { submit() }
                            )

                            else -> CredentialsForm(
                                isSignup = mode == AuthMode.Signup,
                                userId = userId,
                                onUserIdChange = { newValue ->
                                    if (mode == AuthMode.Signup) {
                                        if (newValue.length <= 20 && newValue.matches(IdRegex)) {
                                            userId = newValue
                                            signupViewModel.resetIdCheck()
                                            message = null
                                        }
                                    } else {
                                        userId = newValue
                                        message = null
                                    }
                                },
                                password = password,
                                onPasswordChange = { newValue ->
                                    if (
                                        mode != AuthMode.Signup ||
                                        (newValue.length <= 64 && newValue.matches(PasswordRegex))
                                    ) {
                                        password = newValue
                                        message = null
                                    }
                                },
                                passwordConfirm = passwordConfirm,
                                onPasswordConfirmChange = { newValue ->
                                    if (newValue.length <= 64 && newValue.matches(PasswordRegex)) {
                                        passwordConfirm = newValue
                                        message = null
                                    }
                                },
                                email = email,
                                onEmailChange = {
                                    email = it
                                    message = null
                                },
                                signupViewModel = signupViewModel,
                                userIdFocusRequester = userIdFocusRequester,
                                passwordFocusRequester = passwordFocusRequester,
                                passwordConfirmFocusRequester = passwordConfirmFocusRequester,
                                emailFocusRequester = emailFocusRequester,
                                onDone = { submit() }
                            )
                        }
                    }

                    /* 경고 / 안내 메시지 */
                    AnimatedContent(
                        targetState = shownMessage,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                        },
                        label = "message"
                    ) { shown ->
                        if (shown != null) {
                            Text(
                                text = shown.text,
                                color = if (shown.isError) NeuDanger else NeuAccent,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    /* 로그인 상태 유지 + 아이디 찾기 | 비밀번호 찾기 */
                    AnimatedVisibility(
                        visible = mode == AuthMode.Login,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .toggleable(
                                        value = rememberMe,
                                        role = Role.Checkbox,
                                        onValueChange = { rememberMe = it }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeuAccent,
                                        uncheckedColor = NeuMuted,
                                        checkmarkColor = NeuBg
                                    )
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Text(
                                    text = "로그인 상태 유지",
                                    color = NeuMuted,
                                    fontSize = 12.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuthLink(
                                    text = "아이디 찾기",
                                    onClick = { switchMode(AuthMode.FindId) }
                                )

                                Text(
                                    text = "|",
                                    color = NeuMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )

                                AuthLink(
                                    text = "비밀번호 찾기",
                                    onClick = { switchMode(AuthMode.FindPassword) }
                                )
                            }
                        }
                    }
                }

                /* 카드 위에 걸친 아이콘 배지 */
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(BadgeSize)
                        .neuRaised(CircleShape, 6.dp, NeuAccent),
                    contentAlignment = Alignment.Center
                ) {
                    ShieldLockIcon(
                        color = NeuBg,
                        // 방패 아래가 뾰족해 위로 떠 보여서 살짝 내려 시각적 중심을 맞춘다
                        modifier = Modifier
                            .offset(y = 3.dp)
                            .size(50.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            /* 회원가입 / 로그인으로 돌아가기 */
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                },
                label = "bottomLink"
            ) { target ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (target) {
                        AuthMode.Login -> {
                            Text(
                                text = "계정이 없으신가요?",
                                color = NeuMuted,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            AuthLink(
                                text = "회원가입",
                                fontSize = 13.sp,
                                bold = true,
                                onClick = { switchMode(AuthMode.Signup) }
                            )
                        }

                        AuthMode.Signup -> {
                            Text(
                                text = "이미 계정이 있으신가요?",
                                color = NeuMuted,
                                fontSize = 13.sp
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            AuthLink(
                                text = "로그인",
                                fontSize = 13.sp,
                                bold = true,
                                onClick = { switchMode(AuthMode.Login) }
                            )
                        }

                        else -> AuthLink(
                            text = "로그인으로 돌아가기",
                            fontSize = 13.sp,
                            bold = true,
                            onClick = { switchMode(AuthMode.Login) }
                        )
                    }
                }
            }
        }
    }
}


private fun titleOf(mode: AuthMode) = when (mode) {
    AuthMode.Login -> "로그인"
    AuthMode.Signup -> "회원가입"
    AuthMode.FindId -> "아이디 찾기"
    AuthMode.FindPassword -> "비밀번호 찾기"
}

private fun submitLabelOf(mode: AuthMode) = when (mode) {
    AuthMode.Login -> "LOGIN"
    AuthMode.Signup -> "SIGN UP"
    AuthMode.FindId -> "FIND ID"
    AuthMode.FindPassword -> "FIND PASSWORD"
}

// 로그인과 회원가입은 같은 폼이라 전환 시 폼을 갈아끼우지 않고 늘리기만 한다
private fun formOf(mode: AuthMode) =
    if (mode == AuthMode.Signup) AuthMode.Login else mode


/* 아이디 / 비밀번호 (+ 회원가입일 때 비밀번호 확인, 이메일) */
@Composable
private fun CredentialsForm(
    isSignup: Boolean,
    userId: String,
    onUserIdChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordConfirm: String,
    onPasswordConfirmChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    signupViewModel: SignupViewModel,
    userIdFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester,
    passwordConfirmFocusRequester: FocusRequester,
    emailFocusRequester: FocusRequester,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        /* 아이디 */
        NeuInputField(
            value = userId,
            onValueChange = onUserIdChange,
            placeholder = "아이디",
            leadingIcon = Icons.Default.Person,
            focusRequester = userIdFocusRequester,
            imeAction = ImeAction.Next,
            onImeAction = { passwordFocusRequester.requestFocus() },
            trailingIcon = if (isSignup) {
                {
                    TextButton(
                        onClick = { signupViewModel.checkId(userId.trim()) },
                        enabled = !signupViewModel.isCheckingId && userId.isNotBlank(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = NeuAccent,
                            disabledContentColor = NeuMuted
                        ),
                        shape = NeuFieldShape
                    ) {
                        if (signupViewModel.isCheckingId) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NeuAccent
                            )
                        } else {
                            Text(
                                text = "중복 확인",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else null
        )

        SignupOnly(visible = isSignup) {
            Column {
                FieldGuide("영문, 숫자 4~20자")

                //아이디 중복 확인 결과
                if (signupViewModel.idCheckMessage.isNotBlank()) {
                    Text(
                        text = signupViewModel.idCheckMessage,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        color = if (signupViewModel.isIdAvailable) NeuSuccess else NeuDanger,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        /* 비밀번호 */
        NeuInputField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "비밀번호",
            leadingIcon = Icons.Default.Lock,
            focusRequester = passwordFocusRequester,
            imeAction = if (isSignup) ImeAction.Next else ImeAction.Done,
            onImeAction = {
                if (isSignup) passwordConfirmFocusRequester.requestFocus() else onDone()
            },
            isPassword = true
        )

        /* 회원가입일 때만 아래로 늘어나는 영역 */
        SignupOnly(visible = isSignup) {
            Column {
                FieldGuide("영문, 숫자, 특수문자 (! @ # $ % ^ & *) 8~64자")

                Spacer(modifier = Modifier.height(16.dp))

                //비밀번호 확인
                NeuInputField(
                    value = passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    placeholder = "비밀번호 확인",
                    leadingIcon = Icons.Default.Lock,
                    focusRequester = passwordConfirmFocusRequester,
                    imeAction = ImeAction.Next,
                    onImeAction = { emailFocusRequester.requestFocus() },
                    isPassword = true
                )

                //비밀번호 불일치 즉시 표시
                if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
                    Text(
                        text = "비밀번호가 일치하지 않습니다.",
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                        color = NeuDanger,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                //이메일
                NeuInputField(
                    value = email,
                    onValueChange = onEmailChange,
                    placeholder = "이메일",
                    leadingIcon = Icons.Default.Email,
                    focusRequester = emailFocusRequester,
                    imeAction = ImeAction.Done,
                    onImeAction = onDone,
                    keyboardType = KeyboardType.Email
                )
            }
        }
    }
}


@Composable
private fun FindIdForm(
    email: String,
    onEmailChange: (String) -> Unit,
    emailFocusRequester: FocusRequester,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FindGuide("가입할 때 사용한 이메일을 입력해 주세요.")

        NeuInputField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "이메일",
            leadingIcon = Icons.Default.Email,
            focusRequester = emailFocusRequester,
            imeAction = ImeAction.Done,
            onImeAction = onDone,
            keyboardType = KeyboardType.Email
        )
    }
}


@Composable
private fun FindPasswordForm(
    userId: String,
    onUserIdChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    userIdFocusRequester: FocusRequester,
    emailFocusRequester: FocusRequester,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FindGuide("가입한 아이디와 이메일을 입력해 주세요.")

        NeuInputField(
            value = userId,
            onValueChange = onUserIdChange,
            placeholder = "아이디",
            leadingIcon = Icons.Default.Person,
            focusRequester = userIdFocusRequester,
            imeAction = ImeAction.Next,
            onImeAction = { emailFocusRequester.requestFocus() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        NeuInputField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "이메일",
            leadingIcon = Icons.Default.Email,
            focusRequester = emailFocusRequester,
            imeAction = ImeAction.Done,
            onImeAction = onDone,
            keyboardType = KeyboardType.Email
        )
    }
}


/* 기존 입력 칸 디자인 (눌린 면 + 포인트 색 아이콘) */
@Composable
private fun NeuInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,

        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .neuInset(NeuFieldShape)
            .focusRequester(focusRequester),

        placeholder = {
            Text(
                text = placeholder,
                color = NeuMuted
            )
        },

        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = NeuAccent
            )
        },

        trailingIcon = when {
            isPassword -> {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible }
                    ) {
                        Icon(
                            imageVector =
                                if (passwordVisible)
                                    Icons.Default.Visibility
                                else
                                    Icons.Default.VisibilityOff,
                            contentDescription =
                                if (passwordVisible)
                                    "비밀번호 숨기기"
                                else
                                    "비밀번호 보기",
                            tint = NeuAccent
                        )
                    }
                }
            }

            else -> trailingIcon
        },

        visualTransformation =
            if (isPassword && !passwordVisible)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,

        singleLine = true,

        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),

        keyboardActions = KeyboardActions(
            onAny = { onImeAction() }
        ),

        colors = neuTextFieldColors(),

        shape = NeuFieldShape
    )
}


@Composable
private fun SignupOnly(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(320), expandFrom = Alignment.Top) +
                fadeIn(tween(220, delayMillis = 100)),
        exit = shrinkVertically(tween(280), shrinkTowards = Alignment.Top) +
                fadeOut(tween(120))
    ) {
        content()
    }
}

@Composable
private fun FieldGuide(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        color = NeuMuted,
        fontSize = 12.sp
    )
}

@Composable
private fun FindGuide(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        color = NeuMuted,
        fontSize = 13.sp
    )
}

@Composable
private fun AuthLink(
    text: String,
    onClick: () -> Unit,
    fontSize: TextUnit = 12.sp,
    bold: Boolean = false
) {
    Text(
        text = text,
        color = NeuAccent,
        fontSize = fontSize,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
    )
}


/* 방패 + 자물쇠 (선 아이콘) */
@Composable
private fun ShieldLockIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val line = Stroke(
            width = s * 0.060f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // 방패
        val shield = Path().apply {
            moveTo(s * 0.50f, s * 0.135f)
            lineTo(s * 0.81f, s * 0.235f)
            lineTo(s * 0.81f, s * 0.45f)
            cubicTo(
                s * 0.81f, s * 0.68f,
                s * 0.66f, s * 0.80f,
                s * 0.50f, s * 0.865f
            )
            cubicTo(
                s * 0.34f, s * 0.80f,
                s * 0.19f, s * 0.68f,
                s * 0.19f, s * 0.45f
            )
            lineTo(s * 0.19f, s * 0.235f)
            close()
        }
        drawPath(shield, color = color, style = line)

        // 자물쇠 고리
        val shackle = Path().apply {
            moveTo(s * 0.41f, s * 0.43f)
            lineTo(s * 0.41f, s * 0.39f)
            arcTo(
                rect = Rect(
                    center = Offset(s * 0.50f, s * 0.39f),
                    radius = s * 0.09f
                ),
                startAngleDegrees = 180f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(s * 0.59f, s * 0.43f)
        }
        drawPath(shackle, color = color, style = line)

        // 자물쇠 몸통
        drawRoundRect(
            color = color,
            topLeft = Offset(s * 0.35f, s * 0.43f),
            size = Size(s * 0.30f, s * 0.22f),
            cornerRadius = CornerRadius(s * 0.04f),
            style = line
        )
    }
}
