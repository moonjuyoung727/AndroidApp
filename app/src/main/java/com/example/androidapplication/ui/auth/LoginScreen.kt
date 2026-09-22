package com.example.androidapplication.ui.auth

import androidx.compose.foundation.shape.CircleShape
import com.example.androidapplication.ui.theme.*

import com.example.androidapplication.viewmodel.LoginViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch




// 로그인 화면 생성

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit = {},
    onFindIdClick: () -> Unit = {},
    onFindPasswordClick: () -> Unit = {},
    onSignupClick: () -> Unit = {}
) {

    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var message by remember { mutableStateOf<String?>(null) }
    var isLoginError by remember { mutableStateOf(false) }

    val userIdFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(
        viewModel.loginSuccess
            ) {
        if (viewModel.loginSuccess) {
            onLoginSuccess()
            viewModel.consumeLoginSuccess()
        }
    }

    fun validateAndLogin() {

        // 아이디 확인
        if (userId.isBlank()) {

            message = "아이디를 입력해 주세요."
            isLoginError = false

            userIdFocusRequester.requestFocus()

            return
        }

        //비밀번호 확인
        if (password.isBlank()) {

            message = "비밀번호를 입력해 주세요."
            isLoginError = false

            passwordFocusRequester.requestFocus()

            return
        }

        viewModel.login(
            id = userId.trim(),
            password = password
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBg)
            ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(modifier = Modifier.height(140.dp))
            Text(
                text = "로그인",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = NeuAccent,
            )

            Spacer(modifier = Modifier.height(70.dp))

            /* 아이디 */

            OutlinedTextField(
                value = userId,

                onValueChange = {
                    userId = it

                    if (!isLoginError) {
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

            Spacer(modifier = Modifier.height(16.dp))

            /* 비밀번호 */
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it

                    if (!isLoginError) {
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
                    Icon (
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
                    imeAction = ImeAction.Done
                ),

                keyboardActions = KeyboardActions(
                    onDone = {
                        validateAndLogin()
                    }
                ),

                colors = neuTextFieldColors(),

                shape = NeuFieldShape
            )

            /* 경고 메시지 */
            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(

                    text = message!!,

                    color =
                        if (isLoginError)
                            NeuDanger
                        else
                            NeuAccent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            /* 로그인 버튼 */
            NeuButton(
                onClick = {
                    validateAndLogin()
                },

                enabled = !viewModel.isLoading,

                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),

            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = NeuBg
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(text = "로그인 중 ...")
                } else {
                    Text(
                        text = "로그인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (viewModel.loginMessage.isNotBlank()) {
                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = viewModel.loginMessage,
                    color =
                        if (viewModel.isLoginError)
                            NeuDanger
                        else
                            NeuAccent,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onFindIdClick
            ) {

                    Text(
                        text = "아이디 찾기",
                        color = NeuAccent,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "|",
                    color = NeuMuted
                )

                TextButton(
                    onClick = onFindPasswordClick
            ) {
                    Text(
                        text = "비밀번호 찾기",
                        color = NeuAccent,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "|",
                    color = NeuMuted
                )

                TextButton(
                    onClick = onSignupClick
            ) {
                    Text(
                        text = "회원가입",
                        color = NeuAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            }
        }
    }
}



//@Preview(showBackground = true)
//@Composable
//fun LoginScreenPreview() {
//    val repository = LoginRepository(RetrofitClient.loginApi)
//    val viewModel = LoginViewModel(repository)
//
//    LoginScreen(viewModel)
//}