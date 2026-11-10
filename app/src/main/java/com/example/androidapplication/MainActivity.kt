package com.example.androidapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.example.androidapplication.core.designsystem.NeuBg
import com.example.androidapplication.core.designsystem.NeuTheme
import com.example.androidapplication.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(NeuBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(NeuBg.toArgb())
        )
        setContent {
            NeuTheme { AppRoot() }
        }
    }
}
