package com.example.androidapplication.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import com.example.androidapplication.core.data.PrivacyZone
import com.example.androidapplication.core.media.MaskGeometry.toRect
import com.example.androidapplication.core.webrtc.WhepSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.GlTextureFrameBuffer
import org.webrtc.VideoFrame
import org.webrtc.VideoFrameDrawer
import org.webrtc.VideoSink
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 수신 트랙의 다음 프레임을 원본 해상도 Bitmap으로 받는다. 줌과 화면 오버레이의 영향을 받지 않는다. */
class FrameGrabber(private val eglContext: EglBase.Context) {
    private val thread = HandlerThread("snapshot-gl").apply { start() }
    private val handler = Handler(thread.looper)

    suspend fun grab(session: WhepSession, timeoutMs: Long): Bitmap? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val taken = AtomicBoolean(false)
            val sink = object : VideoSink {
                override fun onFrame(frame: VideoFrame) {
                    if (!taken.compareAndSet(false, true)) return
                    session.removeSink(this)
                    frame.retain()
                    val posted = handler.post {
                        try {
                            val bitmap = render(frame)
                            if (cont.isActive) cont.resume(bitmap) else bitmap.recycle()
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                        } finally {
                            frame.release()
                        }
                    }
                    if (!posted) frame.release()
                }
            }
            session.addSink(sink)
            cont.invokeOnCancellation {
                taken.set(true)
                session.removeSink(sink)
            }
        }
    }

    /** 계측 테스트에서 합성 프레임을 바로 변환할 때 쓴다. */
    internal fun renderOnThisThread(frame: VideoFrame): Bitmap = render(frame)

    private fun render(frame: VideoFrame): Bitmap {
        val width = frame.rotatedWidth
        val height = frame.rotatedHeight
        val egl = EglBase.create(eglContext, EglBase.CONFIG_PIXEL_BUFFER)
        val drawer = GlRectDrawer()
        val frameDrawer = VideoFrameDrawer()
        val fbo = GlTextureFrameBuffer(GLES20.GL_RGBA)
        try {
            egl.createDummyPbufferSurface()
            egl.makeCurrent()
            fbo.setSize(width, height)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.frameBufferId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fbo.textureId, 0
            )
            // glReadPixels는 아래쪽부터 읽으므로 Bitmap용으로 뒤집어 그린다
            val flip = Matrix().apply {
                preTranslate(0.5f, 0.5f)
                preScale(1f, -1f)
                preTranslate(-0.5f, -0.5f)
            }
            frameDrawer.drawFrame(frame, drawer, flip, 0, 0, width, height)
            val pixels = ByteBuffer.allocateDirect(width * height * 4)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pixels) }
        } finally {
            fbo.release()
            frameDrawer.release()
            drawer.release()
            egl.release()
        }
    }

    fun release() {
        thread.quitSafely()
    }
}

/** 활성 존을 불투명한 검정 사각형으로 픽셀에 직접 그려 넣는다. */
fun burnMasks(bitmap: Bitmap, zones: List<PrivacyZone>): Int {
    val active = MaskGeometry.active(zones)
    if (active.isEmpty()) return 0
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    active.forEach { canvas.drawRect(MaskGeometry.toPixels(it, bitmap.width, bitmap.height).toRect(), paint) }
    return active.size
}
