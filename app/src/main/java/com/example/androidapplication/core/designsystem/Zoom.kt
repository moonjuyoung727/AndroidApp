package com.example.androidapplication.core.designsystem

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.serialization.Serializable

/** 위치는 비율로 저장해 회전해도 보던 영역이 유지된다. */
@Serializable
data class ZoomView(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    val isZoomed: Boolean get() = scale > ZoomMath.MIN
}

object ZoomMath {
    const val MIN = 1f
    const val MAX = 4f
    const val STEP = 0.5f

    /** 확대된 영상이 항상 화면을 채우도록 범위를 제한한다. */
    fun clamp(view: ZoomView): ZoomView {
        val s = view.scale.coerceIn(MIN, MAX)
        val min = 1f - s
        return ZoomView(s, view.x.coerceIn(min, 0f), view.y.coerceIn(min, 0f))
    }

    /** (px, py) 지점이 확대 전후에 같은 자리에 머물도록 이동량을 보정한다. */
    fun zoomAround(view: ZoomView, nextScale: Float, px: Float, py: Float): ZoomView {
        val s = nextScale.coerceIn(MIN, MAX)
        val ratio = s / view.scale
        return clamp(ZoomView(s, px - (px - view.x) * ratio, py - (py - view.y) * ratio))
    }

    fun zoomIn(view: ZoomView) = zoomAround(view, view.scale + STEP, 0.5f, 0.5f)
    fun zoomOut(view: ZoomView) = zoomAround(view, view.scale - STEP, 0.5f, 0.5f)

    fun pan(view: ZoomView, dx: Float, dy: Float) = clamp(view.copy(x = view.x + dx, y = view.y + dy))

    /** 두 손가락 중심 기준 확대 후 중심 이동만큼 옮긴다. */
    fun gesture(view: ZoomView, cx: Float, cy: Float, factor: Float, dx: Float, dy: Float): ZoomView =
        pan(zoomAround(view, view.scale * factor, cx, cy), dx, dy)

    fun doubleTap(view: ZoomView, px: Float, py: Float): ZoomView =
        if (view.isZoomed) ZoomView() else zoomAround(view, 2f, px, py)
}

/** 영상과 마스크를 함께 변환하는 레이어. 부모는 clipToBounds 해야 한다. */
fun Modifier.zoomLayer(view: ZoomView): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = view.scale
    scaleY = view.scale
    translationX = view.x * size.width
    translationY = view.y * size.height
}

/** 핀치(두 손가락 중심 기준), 한 손가락 이동(확대 상태에서만), 더블탭. 확대 전 한 손가락은 부모 스크롤에 넘긴다. */
fun Modifier.zoomGestures(view: () -> ZoomView, onChange: (ZoomView) -> Unit): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { offset: Offset ->
            onChange(ZoomMath.doubleTap(view(), offset.x / size.width, offset.y / size.height))
        })
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2 || view().isZoomed) {
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (zoom != 1f || pan != Offset.Zero) {
                        val centroid = event.calculateCentroid(useCurrent = false)
                        onChange(
                            ZoomMath.gesture(
                                view(), centroid.x / size.width, centroid.y / size.height,
                                zoom, pan.x / size.width, pan.y / size.height
                            )
                        )
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

/** 전체 화면 영상: 가로 몰입 모드로 전환하고 벗어나면 되돌린다. */
@Composable
fun ImmersiveLandscape() {
    val activity: Activity? = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
