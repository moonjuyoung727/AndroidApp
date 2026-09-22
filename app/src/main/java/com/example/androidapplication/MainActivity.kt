package com.example.androidapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.androidapplication.navigation.AppNavHost
import com.example.androidapplication.ui.auth.LoginScreen
import com.example.androidapplication.ui.theme.NeuTheme

class MainActivity : ComponentActivity() {
    // 앱 처음 실행될 때 시작되는 화면

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
            // 화면 띄우기
        setContent {
            NeuTheme {
                AppNavHost()
            }
        }
    }
}