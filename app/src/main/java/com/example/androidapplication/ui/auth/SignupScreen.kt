package com.example.androidapplication.ui.auth

import androidx.compose.foundation.shape.CircleShape
import com.example.androidapplication.ui.theme.*

import com.example.androidapplication.viewmodel.SignupViewModel

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapplication.viewmodel.LoginViewModel
import kotlinx.coroutines.launch



@Composable
fun SignupScreen (
    viewModel: SignupViewModel,
    onSignupSuccess: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }

    //전체 회원가입 오류 메시지
    var message by remember { mutableStateOf<String?>(null) }

    val userIdFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val passwordConfirmFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }

    //아이디 영문 대소문자 + 숫자만 허용
    val idRegex = Regex("^[A-Za-z0-9]*$")

    //비밀번호 영문 대소문자 +  숫자 + 특수문자
    val passwordRegex = Regex("^[A-Za-z0-9!@#\$%^&*]*$")

    LaunchedEffect(viewModel.signupSuccess) {
        if (viewModel.signupSuccess) {
            onSignupSuccess()
            viewModel.consumeSignupSuccess()
        }
    }

    //회원가입 버튼 클릭
    fun validateAndSignup() {
        message = null

        //아이디 입력 여부
        if (userId.isBlank()) {

            message = "아이디를 입력해 주세요."
            userIdFocusRequester.requestFocus()

            return
        }

        //아이디 길이
        if (userId.length !in 4..20) {

            message = "아이디는 4~20자로 입력해 주세요."
            userIdFocusRequester.requestFocus()

            return
        }

        //아이디 문자 검사
        if (!userId.matches(idRegex)) {

            message = "아이디는 영문과 숫자만 사용할 수 있습니다."
            userIdFocusRequester.requestFocus()

            return
        }

        //중복 확인 여부
        if (!viewModel.isIdChecked) {
            message = "아이디 중복 확인을 해주세요."
            userIdFocusRequester.requestFocus()

            return
        }

        if (!viewModel.isIdAvailable) {
            message = "사용할 수 없는 아이디입니다."
            userIdFocusRequester.requestFocus()

            return
        }

        //비밀번호
        if (password.isBlank()) {

            message = "비밀번호를 입력해 주세요."
            passwordFocusRequester.requestFocus()

            return
        }

        //비밀번호 길이
        if (password.length !in 8..64) {

            message = "비밀번호는 8~64자로 입력해 주세요."
            passwordFocusRequester.requestFocus()

            return
        }

        //비밀번호 허용 문자
        if (!password.matches(passwordRegex)) {
            message = "비밀번호는 영문, 숫자, ! @ # $ % ^ & * 만 사용할 수 있습니다."
            passwordFocusRequester.requestFocus()

            return
        }

        //비밀번호 확인
        if (passwordConfirm.isBlank()) {

            message = "비밀번호 확인을 입력해 주세요."
            passwordConfirmFocusRequester.requestFocus()

            return
        }

        //비밀번호 일치 검사
        if (password != passwordConfirm) {

            message = "비밀번호가 일치하지 않습니다."
            passwordConfirmFocusRequester.requestFocus()

            return
        }

        //이메일
        if (email.isBlank()) {

            message = "이메일을 입력해 주세요."
            emailFocusRequester.requestFocus()

            return
        }

        //이메일 형식
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {

            message = "올바른 이메일 형식을 입력해 주세요."
            emailFocusRequester.requestFocus()

            return
        }

        viewModel.signup(
            id = userId.trim(),
            password = password,
            email = email.trim()
        )
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
        ) {
        Spacer(modifier = Modifier.height(25.dp))

        IconButton(
            onClick = onBackClick,
            modifier = Modifier.neuRaised(CircleShape, 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "뒤로가기",
                tint = NeuAccent
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "회원가입",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = NeuAccent
        )

        Spacer(modifier = Modifier.height(32.dp))

        //아이디
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = userId,
                onValueChange = { newValue ->
                    if (
                        newValue.length <= 20 &&
                        newValue.matches(idRegex)
        ) {
                        userId = newValue
                        viewModel.resetIdCheck()
                        message = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .neuInset(NeuFieldShape)
                    .focusRequester(userIdFocusRequester),

                placeholder = {
                    Text(
                        text = "아이디",
                        color = NeuMuted
                    )
                },

                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = NeuAccent
                    )
                },

                trailingIcon = {
                    TextButton(
                        onClick = {
                            viewModel.checkId(userId.trim())
                        },
                        enabled =
                            !viewModel.isCheckingId &&
                                    userId.isNotBlank(),

                        colors = ButtonDefaults.textButtonColors(
                            contentColor = NeuAccent,
                            disabledContentColor = NeuMuted
                        ),
                        shape = NeuFieldShape
        ) {
                        if (viewModel.isCheckingId) {
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
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),

                keyboardActions = KeyboardActions(
                    onNext = {
                        passwordFocusRequester.requestFocus()
                    }
                ),

                colors = neuTextFieldColors(),
                shape = NeuFieldShape
            )

            // 아이디 규칙 + 중복 확인 버튼
            Text(
                text = "    영문, 숫자 4~20자",
                color = NeuMuted,
                fontSize = 12.sp
            )
        }

        //아이디 중복 확인 규칙
        if (viewModel.idCheckMessage.isNotBlank()) {
            Text(
                text = viewModel.idCheckMessage,
                modifier = Modifier.padding(
                    start = 5.dp,
                    top = 6.dp
                ),
                color =
                    if (viewModel.isIdAvailable)
                        NeuSuccess
                    else
                        NeuDanger,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))


        //비밀번호
        OutlinedTextField(
            value = password,
            onValueChange = { newValue ->
                if (
                    newValue.length <= 64 &&
                    newValue.matches(passwordRegex)
        ) {
                    password = newValue
                    message = null
                }
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                    .neuInset(NeuFieldShape)
                .focusRequester(passwordFocusRequester),

            placeholder = {
                Text(
                    text = "비밀번호",
                    color = NeuMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NeuAccent
                )
            },

            trailingIcon = {
                IconButton(
                    onClick = {
                        passwordVisible = !passwordVisible
                    }
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
            },

            visualTransformation =
                if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
            singleLine = true,

            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),

            keyboardActions = KeyboardActions(
                onNext = { passwordConfirmFocusRequester.requestFocus() }
            ),

            colors = neuTextFieldColors(),

            shape = NeuFieldShape
        )

        Text(
            text = "    영문, 숫자, 특수문자 (! @ # $ % ^ & *) 8~64자",
            modifier = Modifier.padding(top = 5.dp),
            color = NeuMuted,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        //비밀번호 확인
        OutlinedTextField(
            value = passwordConfirm,
            onValueChange = { newValue ->
                if (
                    newValue.length <= 64 &&
                    newValue.matches(passwordRegex)
        ) {
                    passwordConfirm = newValue
                    message = null
                }
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                    .neuInset(NeuFieldShape)
                .focusRequester(passwordConfirmFocusRequester),

            placeholder = {
                Text(
                    text = "비밀번호 확인",
                    color = NeuMuted
                )
            },

            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NeuAccent
                )
            },

            trailingIcon = {
                IconButton(
                    onClick = {
                        passwordConfirmVisible = !passwordConfirmVisible
                    }
        ) {
                    Icon(
                        imageVector =
                            if (passwordConfirmVisible)
                                Icons.Default.Visibility
                            else
                                Icons.Default.VisibilityOff,

                        contentDescription = null,

                        tint = NeuAccent
                    )
                }
            },

            visualTransformation =
                if (passwordConfirmVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),

            singleLine = true,

            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),

            keyboardActions = KeyboardActions(
                onNext = {
                    emailFocusRequester.requestFocus()
                }
            ),

            colors = neuTextFieldColors(),

            shape = NeuFieldShape
        )

        //비밀번호 불일치 즉시 표시
        if (
            passwordConfirm.isNotEmpty() &&
            password != passwordConfirm
        ) {

            Text(
                text = "비밀번호가 일치하지 않습니다.",
                modifier = Modifier.padding(
                    start = 5.dp,
                    top = 5.dp
                ),
                color = NeuDanger,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        //이메일
        OutlinedTextField(

            value = email,

            onValueChange = {
                email = it
                message = null
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                    .neuInset(NeuFieldShape)
                .focusRequester(emailFocusRequester),

            placeholder = {

                Text(
                    text = "이메일",
                    color = NeuMuted
                )
            },

            singleLine = true,

            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),

            keyboardActions = KeyboardActions(
                onDone = {
                    validateAndSignup()
                }
            ),

            colors = neuTextFieldColors(),

            shape = NeuFieldShape
        )


        /*
             * 전체 오류 메시지
             */
        if (message != null) {

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message!!,
                color = NeuDanger,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }


        Spacer(modifier = Modifier.height(28.dp))


        /*
             * =========================
             * 회원가입 버튼
             * =========================
             */

        NeuButton(

            onClick = {
                validateAndSignup()
            },

            enabled = !viewModel.isSigningUp,

            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),

        ) {

            if (viewModel.isSigningUp) {

                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = NeuBg
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "회원가입 중...",
                    fontSize = 16.sp
                )

            } else {

                Text(
                    text = "회원가입",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


