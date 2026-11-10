package com.example.androidapplication.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* 색상은 WebDashboard와 같은 토큰을 유지한다. 면과 선은 배경 위의 흰색 투명도로 만든다. */

val NeuBg = Color(0xFF2B2E34)
val NeuAccent = Color(0xFFBDD9D7)
val NeuAccentPressed = Color(0xFFA9CBC9)
val NeuMuted = Color(0xFF9BA4AC)
val NeuDanger = Color(0xFFD97878)
val NeuSuccess = Color(0xFF6FBF9A)
val NeuWarning = Color(0xFFD6A85F)

val NeuLine = Color(0x0DFFFFFF)
val NeuBorder = Color(0x12FFFFFF)
val NeuSurface = Color(0x0DFFFFFF)
val NeuSurfaceStrong = Color(0x17FFFFFF)

val NeuCardShape = RoundedCornerShape(24.dp)
val NeuFieldShape = RoundedCornerShape(16.dp)
val NeuSmallShape = RoundedCornerShape(12.dp)

/** 이벤트 종류 색상. 순서를 바꾸지 말 것 (색각이상 대비 검증 순서). */
val EventTypeColors: Map<String, Color> = mapOf(
    "motion" to Color(0xFF6B85B3),
    "sound" to Color(0xFFA77BC7),
    "camera_connect" to Color(0xFF63BFA3),
    "camera_disconnect" to Color(0xFFB8CC63),
    "vpn_connect" to Color(0xFFE08A6B),
    "vpn_disconnect" to Color(0xFF8F9AA8)
)

fun eventColor(type: String): Color = EventTypeColors[type] ?: NeuMuted

fun vpnStatusColor(status: String?): Color = when (status) {
    "connected" -> NeuSuccess
    "error" -> NeuDanger
    else -> NeuMuted
}

private val AppColorScheme = darkColorScheme(
    primary = NeuAccent,
    onPrimary = NeuBg,
    secondary = NeuAccent,
    onSecondary = NeuBg,
    background = NeuBg,
    onBackground = NeuAccent,
    surface = NeuBg,
    onSurface = NeuAccent,
    surfaceVariant = NeuBg,
    onSurfaceVariant = NeuMuted,
    surfaceContainer = NeuBg,
    surfaceContainerHigh = NeuBg,
    surfaceContainerHighest = NeuBg,
    surfaceContainerLow = NeuBg,
    surfaceContainerLowest = NeuBg,
    surfaceTint = Color.Transparent,
    outline = NeuMuted,
    outlineVariant = NeuLine,
    error = NeuDanger,
    onError = NeuBg
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun NeuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = Shapes(small = NeuSmallShape, medium = NeuFieldShape, large = NeuCardShape),
        content = content
    )
}

/** 평면 카드 면: 배경보다 조금 밝은 면과 얇은 테두리. */
fun Modifier.surfaceCard(shape: Shape = NeuCardShape): Modifier = this
    .clip(shape)
    .background(NeuSurface)
    .border(1.dp, NeuBorder, shape)

/** 포인트 색으로 채운 주요 버튼. */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = NeuAccent,
            contentColor = NeuBg,
            disabledContainerColor = NeuSurface,
            disabledContentColor = NeuMuted
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun neuTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NeuAccent,
    unfocusedTextColor = NeuAccent,
    errorTextColor = NeuAccent,
    disabledTextColor = NeuMuted,
    focusedContainerColor = NeuSurface,
    unfocusedContainerColor = NeuSurface,
    errorContainerColor = NeuSurface,
    disabledContainerColor = NeuSurface,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    focusedBorderColor = NeuAccent.copy(alpha = 0.45f),
    errorBorderColor = NeuDanger,
    focusedLabelColor = NeuMuted,
    unfocusedLabelColor = NeuMuted,
    disabledLabelColor = NeuMuted,
    cursorColor = NeuAccent,
    errorCursorColor = NeuDanger
)
