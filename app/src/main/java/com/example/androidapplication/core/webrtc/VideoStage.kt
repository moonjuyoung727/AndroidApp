package com.example.androidapplication.core.webrtc

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.androidapplication.core.data.PrivacyZone
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.CountDownLatch

/**
 * TextureView 기반 렌더러. SurfaceView와 달리 Compose의 변환과 겹침이 그대로 적용되어
 * 프라이버시 존 마스크가 영상과 같은 레이어에서 함께 확대·이동된다.
 */
class TextureVideoRenderer(context: Context) : TextureView(context), TextureView.SurfaceTextureListener, VideoSink {
    private val renderer = EglRenderer("securecam-video")
    private var released = false

    fun init(eglContext: EglBase.Context) {
        renderer.init(eglContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        surfaceTextureListener = this
    }

    override fun onFrame(frame: VideoFrame) = renderer.onFrame(frame)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) renderer.setLayoutAspectRatio(w.toFloat() / h)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.createEglSurface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val latch = CountDownLatch(1)
        renderer.releaseEglSurface(latch::countDown)
        latch.await()
        return true
    }

    fun clear() = renderer.clearImage()

    fun release() {
        if (released) return
        released = true
        renderer.release()
    }
}

/** 영상과 활성 프라이버시 존을 같은 레이어에 그린다. 바깥에서 비율과 변환을 정한다. */
@Composable
fun VideoLayer(
    session: WhepSession?,
    eglContext: EglBase.Context,
    zones: List<PrivacyZone>,
    modifier: Modifier = Modifier
) {
    val rendererHolder = androidx.compose.runtime.remember { arrayOfNulls<TextureVideoRenderer>(1) }
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> TextureVideoRenderer(ctx).apply { init(eglContext); rendererHolder[0] = this } },
            onRelease = { it.release() }
        )
        DisposableEffect(session) {
            val renderer = rendererHolder[0]
            if (renderer != null) session?.addSink(renderer)
            onDispose {
                if (renderer != null) {
                    session?.removeSink(renderer)
                    renderer.clear()
                }
            }
        }
        PrivacyMask(zones, Modifier.fillMaxSize())
    }
}

@Composable
fun PrivacyMask(zones: List<PrivacyZone>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        zones.filter { it.enabled }.forEach { zone ->
            drawRect(
                color = Color.Black,
                topLeft = Offset(zone.x * size.width, zone.y * size.height),
                size = Size(zone.width * size.width, zone.height * size.height)
            )
        }
    }
}
