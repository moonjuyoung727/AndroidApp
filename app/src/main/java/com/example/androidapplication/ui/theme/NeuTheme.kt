package com.example.androidapplication.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* =========================================================
 * Dark Neumorphism — WebDashboard 와 동일한 토큰
 * 포인트 색(NeuAccent = #BDD9D7)은 유지
 * ========================================================= */

val NeuBg = Color(0xFF2B2E34)
val NeuAccent = Color(0xFFBDD9D7)
val NeuMuted = Color(0xFF9BA4AC)
val NeuDanger = Color(0xFFD97878)
val NeuSuccess = Color(0xFF6FBF9A)
val NeuWarning = Color(0xFFD6A85F)

val NeuLine = Color(0x0DFFFFFF)

private val ShadowDark = Color(0x80000000)
private val ShadowLight = Color(0x14FFFFFF)

val NeuCardShape = RoundedCornerShape(18.dp)
val NeuFieldShape = RoundedCornerShape(16.dp)
val NeuButtonShape = RoundedCornerShape(16.dp)

private val NeuColorScheme = darkColorScheme(
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

@Composable
fun NeuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeuColorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = NeuFieldShape,
            large = NeuCardShape
        ),
        content = content
    )
}

/* =========================================================
 * 그림자 모디파이어
 * ========================================================= */

private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

private fun shadowPaint(fill: Color, blur: Float, dx: Float, dy: Float, shadow: Color) =
    Paint().apply {
        asFrameworkPaint().apply {
            isAntiAlias = true
            color = fill.toArgb()
            setShadowLayer(blur, dx, dy, shadow.toArgb())
        }
    }

/**
 * 튀어나온 면: 왼쪽 위에서 빛이 들어오는 두 개의 그림자.
 * 배경색(color)으로 채워서 그리므로 별도의 background 는 필요 없다.
 * (setShadowLayer 는 API 28 미만에서는 그림자가 생략되고 면만 그려진다)
 */
fun Modifier.neuRaised(
    shape: Shape = NeuCardShape,
    depth: Dp = 6.dp,
    color: Color = NeuBg
): Modifier = drawWithCache {
    val path = shape.createOutline(size, layoutDirection, this).toPath()
    val d = depth.toPx()
    val dark = shadowPaint(color, d * 2f, d, d, ShadowDark)
    val light = shadowPaint(color, d * 2f, -d, -d, ShadowLight)
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.drawPath(path, dark)
            canvas.drawPath(path, light)
        }
    }
}

/** 눌린 면: 입력창 / 눌린 버튼 / 선택된 항목. */
fun Modifier.neuInset(
    shape: Shape = NeuFieldShape,
    depth: Dp = 4.dp,
    color: Color = NeuBg
): Modifier = drawWithCache {
    val path = shape.createOutline(size, layoutDirection, this).toPath()
    val d = depth.toPx()
    val pad = d * 8f
    val frame = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-pad, -pad, size.width + pad, size.height + pad))
        addPath(path)
    }
    val fill = Paint().apply { this.color = color }
    val dark = shadowPaint(color, d * 1.5f, d, d, ShadowDark)
    val light = shadowPaint(color, d * 1.5f, -d, -d, ShadowLight)
    onDrawBehind {
        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipPath(path)
            canvas.drawPath(path, fill)
            canvas.drawPath(frame, dark)
            canvas.drawPath(frame, light)
            canvas.restore()
        }
    }
}

/* =========================================================
 * 공용 컴포넌트
 * ========================================================= */

/** 포인트 색으로 채운 주요 버튼. 누르면 눌린 면이 된다. Button 과 같은 형태로 사용. */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = NeuButtonShape,
    content: @Composable RowScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = if (enabled) NeuAccent else NeuAccent.copy(alpha = 0.6f)

    Button(
        onClick = onClick,
        modifier = modifier.then(
            if (pressed && enabled) Modifier.neuInset(shape, 3.dp, fill)
            else Modifier.neuRaised(shape, if (enabled) 5.dp else 2.dp, fill)
        ),
        enabled = enabled,
        shape = shape,
        interactionSource = source,
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = NeuBg,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = NeuBg.copy(alpha = 0.6f)
        ),
        content = content
    )
}

/** 눌린 면 위에 놓는 OutlinedTextField 색. Modifier.neuInset(NeuFieldShape) 와 함께 사용. */
@Composable
fun neuTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NeuAccent,
    unfocusedTextColor = NeuAccent,
    errorTextColor = NeuAccent,

    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,

    // 테두리 대신 그림자로 구분하고, 포커스일 때만 포인트 색 링
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = NeuAccent.copy(alpha = 0.55f),
    errorBorderColor = NeuDanger,

    cursorColor = NeuAccent,
    errorCursorColor = NeuDanger
)

/** 상단바 / 하단바 가장자리에 떨어지는 부드러운 그림자. bottom=true 면 바 아래쪽으로. */
fun Modifier.neuBarShadow(bottom: Boolean, height: Dp = 14.dp): Modifier = drawBehind {
    val h = height.toPx()
    val shadow = Brush.verticalGradient(
        colors = if (bottom) listOf(Color(0x66000000), Color.Transparent)
        else listOf(Color.Transparent, Color(0x66000000)),
        startY = if (bottom) size.height else -h,
        endY = if (bottom) size.height + h else 0f
    )
    drawRect(
        brush = shadow,
        topLeft = Offset(0f, if (bottom) size.height else -h),
        size = Size(size.width, h)
    )
}
