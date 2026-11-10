package com.example.androidapplication.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.androidapplication.core.data.PrivacyZone
import kotlinx.coroutines.CompletableDeferred
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoFrameDrawer
import org.webrtc.VideoSink
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "VideoRecorder"
private const val MAX_QUEUED_FRAMES = 4
private const val EOS_WAIT_US = 10_000L
private const val EOS_MAX_TRIES = 300

data class SegmentResult(
    val file: File,
    val startedAtMs: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val maskedZoneCount: Int
)

/**
 * 수신 트랙 녹화 파이프라인.
 * VideoSink → 프레임 retain 후 GL 스레드로 전달 → 인코더 Surface에 그리며 마스크 적용 → MediaCodec(H.264) → MediaMuxer(MP4)
 *
 * WebRTC 디코더 스레드를 막지 않는다. 큐가 차면 프레임을 버린다.
 * 작성 중에는 .tmp로 쓰고 정상 종료 시 이름을 바꾼다. 10분마다 파일을 나눈다.
 */
class VideoRecorder(
    private val eglContext: EglBase.Context,
    private val nextFile: () -> File,
    private val onSegment: (SegmentResult) -> Unit,
    private val onFatal: (Throwable) -> Unit
) : VideoSink {
    private val thread = HandlerThread("recorder-gl").apply { start() }
    private val handler = Handler(thread.looper)
    private val queued = AtomicInteger(0)

    @Volatile private var accepting = true
    /** 녹화 중 존이 바뀌면 다음 프레임부터 반영한다. */
    @Volatile var zones: List<PrivacyZone> = emptyList()

    private val bytesFromFinished = AtomicLong(0)
    private val bytesCurrent = AtomicLong(0)
    val writtenBytes: Long get() = bytesFromFinished.get() + bytesCurrent.get()
    val currentSegmentBytes: Long get() = bytesCurrent.get()
    val droppedFrames = AtomicLong(0)

    // 이하 GL 스레드 전용
    private var egl: EglBase? = null
    private var drawer: GlRectDrawer? = null
    private var frameDrawer: VideoFrameDrawer? = null
    private var segment: Segment? = null
    private var encoderWidth = 0
    private var encoderHeight = 0
    private var failed = false

    override fun onFrame(frame: VideoFrame) {
        if (!accepting) return
        if (queued.get() >= MAX_QUEUED_FRAMES) {
            droppedFrames.incrementAndGet()
            return
        }
        frame.retain()
        queued.incrementAndGet()
        val posted = handler.post {
            try {
                if (!failed) encode(frame)
            } catch (e: Exception) {
                failed = true
                Log.e(TAG, "녹화 실패", e)
                onFatal(e)
            } finally {
                frame.release()
                queued.decrementAndGet()
            }
        }
        if (!posted) {
            frame.release()
            queued.decrementAndGet()
        }
    }

    /** 이미 받은 프레임을 처리한 뒤 파일을 마무리한다. 여러 번 불러도 안전하다. */
    fun stop(): CompletableDeferred<Unit> {
        val done = CompletableDeferred<Unit>()
        accepting = false
        val posted = handler.post {
            try {
                finishSegment()
            } catch (e: Exception) {
                Log.e(TAG, "녹화 마무리 실패", e)
            } finally {
                releaseGl()
                thread.quitSafely()
                done.complete(Unit)
            }
        }
        if (!posted) done.complete(Unit)
        return done
    }

    private fun encode(frame: VideoFrame) {
        if (segment == null) {
            encoderWidth = RecordingFormat.evenSize(frame.rotatedWidth)
            encoderHeight = RecordingFormat.evenSize(frame.rotatedHeight)
            startSegment()
        }
        var current = segment ?: return
        if (frame.timestampNs - current.firstFrameNs(frame.timestampNs) >= RecordingFormat.SEGMENT_MS * 1_000_000) {
            finishSegment()
            startSegment()
            current = segment ?: return
        }
        val firstNs = current.firstFrameNs(frame.timestampNs)
        val ptsNs = frame.timestampNs - firstNs
        // 가변 프레임률은 유지하되 시간이 뒤로 가는 프레임은 버린다
        if (current.frames > 0 && ptsNs <= current.lastPtsNs) return

        val gl = egl ?: return
        gl.makeCurrent()
        GLES20.glViewport(0, 0, encoderWidth, encoderHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // 도중에 해상도가 바뀌어도 뷰포트 크기(첫 프레임 기준)에 맞춰 스케일된다
        frameDrawer!!.drawFrame(frame, drawer!!, null, 0, 0, encoderWidth, encoderHeight)
        current.maskedZones = maxOf(current.maskedZones, drawMasks())
        gl.swapBuffers(ptsNs)
        current.lastPtsNs = ptsNs
        current.frames++
        drain(current, endOfStream = false)
    }

    private fun drawMasks(): Int {
        val active = MaskGeometry.active(zones)
        if (active.isEmpty()) return 0
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        active.forEach { zone ->
            val rect = MaskGeometry.toPixels(zone, encoderWidth, encoderHeight)
            GLES20.glScissor(rect.left, MaskGeometry.glScissorY(rect, encoderHeight), rect.width, rect.height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        return active.size
    }

    private fun startSegment() {
        val finalFile = nextFile()
        val tmp = File(finalFile.path + ".tmp")
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, encoderWidth, encoderHeight).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, RecordingFormat.bitrate(encoderWidth, encoderHeight))
            setInteger(MediaFormat.KEY_FRAME_RATE, RecordingFormat.FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, RecordingFormat.I_FRAME_INTERVAL)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val gl = egl ?: EglBase.create(eglContext, EglBase.CONFIG_RECORDABLE).also {
            egl = it
            drawer = GlRectDrawer()
            frameDrawer = VideoFrameDrawer()
        }
        gl.createSurface(surface)
        gl.makeCurrent()

        bytesCurrent.set(0)
        segment = Segment(
            finalFile, tmp, codec, surface,
            MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4),
            System.currentTimeMillis()
        )
    }

    private fun drain(s: Segment, endOfStream: Boolean) {
        if (endOfStream) s.codec.signalEndOfInputStream()
        val info = MediaCodec.BufferInfo()
        var tries = 0
        while (true) {
            val index = s.codec.dequeueOutputBuffer(info, if (endOfStream) EOS_WAIT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || ++tries > EOS_MAX_TRIES) return
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!s.muxerStarted) {
                        s.track = s.muxer.addTrack(s.codec.outputFormat)
                        s.muxer.start()
                        s.muxerStarted = true
                    }
                }
                index >= 0 -> {
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (info.size > 0 && !isConfig && s.muxerStarted) {
                        val buffer = s.codec.getOutputBuffer(index)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        s.muxer.writeSampleData(s.track, buffer, info)
                        s.samples++
                        s.lastSampleUs = info.presentationTimeUs
                        bytesCurrent.addAndGet(info.size.toLong())
                    }
                    s.codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun finishSegment() {
        val s = segment ?: return
        segment = null
        var playable = false
        try {
            drain(s, endOfStream = true)
            playable = s.muxerStarted && s.samples > 0
        } catch (e: Exception) {
            Log.w(TAG, "EOS 처리 실패", e)
            playable = s.muxerStarted && s.samples > 0
        }
        runCatching { s.codec.stop() }
        runCatching { s.codec.release() }
        egl?.releaseSurface()
        s.surface.release()
        if (playable) playable = runCatching { s.muxer.stop() }.isSuccess
        runCatching { s.muxer.release() }

        if (playable && s.tmp.renameTo(s.file)) {
            bytesFromFinished.addAndGet(s.file.length())
            bytesCurrent.set(0)
            onSegment(
                SegmentResult(
                    s.file, s.startedAtMs, s.lastSampleUs / 1000, encoderWidth, encoderHeight,
                    s.file.length(), s.maskedZones
                )
            )
        } else {
            s.tmp.delete()
            bytesCurrent.set(0)
        }
    }

    private fun releaseGl() {
        frameDrawer?.release()
        drawer?.release()
        egl?.release()
        frameDrawer = null
        drawer = null
        egl = null
    }

    private class Segment(
        val file: File,
        val tmp: File,
        val codec: MediaCodec,
        val surface: Surface,
        val muxer: MediaMuxer,
        val startedAtMs: Long
    ) {
        var track = -1
        var muxerStarted = false
        var samples = 0L
        var frames = 0L
        var lastPtsNs = 0L
        var lastSampleUs = 0L
        var maskedZones = 0
        private var firstNs = Long.MIN_VALUE

        /** PTS는 파일마다 첫 프레임을 0으로 맞춘다. */
        fun firstFrameNs(timestampNs: Long): Long {
            if (firstNs == Long.MIN_VALUE) firstNs = timestampNs
            return firstNs
        }
    }
}
