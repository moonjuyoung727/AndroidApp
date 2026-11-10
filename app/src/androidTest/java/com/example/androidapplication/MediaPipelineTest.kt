package com.example.androidapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapplication.core.data.PrivacyZone
import com.example.androidapplication.core.media.AppDatabase
import com.example.androidapplication.core.media.FrameGrabber
import com.example.androidapplication.core.media.MediaEntity
import com.example.androidapplication.core.media.MediaSource
import com.example.androidapplication.core.media.MediaType
import com.example.androidapplication.core.media.RecordingFormat
import com.example.androidapplication.core.media.SegmentResult
import com.example.androidapplication.core.media.VideoRecorder
import com.example.androidapplication.core.media.burnMasks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.webrtc.EglBase
import org.webrtc.JavaI420Buffer
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoFrame
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/** 실제 GL·MediaCodec·MediaMuxer로 저장 파이프라인을 검증한다 (합성 프레임 사용). */
@RunWith(AndroidJUnit4::class)
class MediaPipelineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var egl: EglBase
    private lateinit var dir: File
    private val released = AtomicInteger()
    private var created = 0

    private val width = 640
    private val height = 360

    /** 왼쪽 위 사분면에 마스크. y 뒤집기가 틀리면 왼쪽 아래가 가려져 실패한다. */
    private val topLeftZone = PrivacyZone("z", "n", 0f, 0f, 0.5f, 0.5f, enabled = true)

    @Before fun setUp() {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        egl = EglBase.create()
        dir = File(context.cacheDir, "pipeline-test").apply { deleteRecursively(); mkdirs() }
    }

    @After fun tearDown() {
        egl.release()
        dir.deleteRecursively()
    }

    /** 위쪽 절반은 밝고 아래쪽 절반은 중간 밝기인 I420 프레임. release 횟수를 센다. */
    private fun frame(timestampNs: Long): VideoFrame {
        created++
        val chromaW = width / 2
        val chromaH = height / 2
        val y = ByteBuffer.allocateDirect(width * height)
        repeat(height) { row -> repeat(width) { y.put((if (row < height / 2) 235 else 150).toByte()) } }
        y.rewind()
        val u = ByteBuffer.allocateDirect(chromaW * chromaH).apply { repeat(capacity()) { put(128.toByte()) }; rewind() }
        val v = ByteBuffer.allocateDirect(chromaW * chromaH).apply { repeat(capacity()) { put(128.toByte()) }; rewind() }
        val buffer = JavaI420Buffer.wrap(width, height, y, width, u, chromaW, v, chromaW) { released.incrementAndGet() }
        return VideoFrame(buffer, 0, timestampNs)
    }

    private fun recorder(results: MutableList<SegmentResult>): VideoRecorder {
        var n = 0
        return VideoRecorder(
            egl.eglBaseContext,
            nextFile = { File(dir, "seg${n++}.mp4") },
            onSegment = { results += it },
            onFatal = { throw AssertionError("녹화 실패", it) }
        )
    }

    private fun feed(recorder: VideoRecorder, startNs: Long, count: Int) {
        repeat(count) { i ->
            val f = frame(startNs + i * 33_000_000L)
            recorder.onFrame(f)
            f.release() // 호출자(WebRTC)의 참조를 놓는다
            Thread.sleep(33)
        }
    }

    private fun Bitmap.brightnessAt(fx: Float, fy: Float): Int {
        val c = getPixel((width * fx).toInt(), (height * fy).toInt())
        return (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
    }

    @Test fun mp4_is_playable_and_mask_is_burned_into_pixels() = runBlocking {
        val results = mutableListOf<SegmentResult>()
        val rec = recorder(results)
        rec.zones = listOf(topLeftZone)
        feed(rec, 5_000_000_000L, 60)
        withTimeout(10_000) { rec.stop().await() }

        val segment = results.single()
        assertTrue(segment.file.exists())
        assertFalse("임시 파일이 남으면 안 된다", File(segment.file.path + ".tmp").exists())
        assertEquals(width, segment.width)
        assertEquals(height, segment.height)
        assertEquals(1, segment.maskedZoneCount)
        assertTrue("길이 ${segment.durationMs}ms", segment.durationMs in 1_000..3_000)
        assertTrue("쓴 바이트", rec.writtenBytes > 0)

        val retriever = MediaMetadataRetriever().apply { setDataSource(segment.file.path) }
        val bitmap = retriever.getFrameAtTime(1_000_000)!!
        retriever.release()
        assertTrue("가려진 영역 ${bitmap.brightnessAt(.25f, .25f)}", bitmap.brightnessAt(.25f, .25f) < 40)
        assertTrue("오른쪽 위 ${bitmap.brightnessAt(.75f, .25f)}", bitmap.brightnessAt(.75f, .25f) > 180)
        assertTrue("왼쪽 아래 ${bitmap.brightnessAt(.25f, .75f)}", bitmap.brightnessAt(.25f, .75f) in 90..180)
    }

    @Test fun every_frame_is_released_even_when_dropped() = runBlocking {
        val results = mutableListOf<SegmentResult>()
        val rec = recorder(results)
        // 대기 없이 밀어 넣어 큐를 넘치게 한다
        repeat(200) { i ->
            val f = frame(1_000_000_000L + i * 33_000_000L)
            rec.onFrame(f)
            f.release()
        }
        withTimeout(10_000) { rec.stop().await() }
        assertEquals("VideoFrame 누수", created, released.get())
        assertTrue("드롭 동작", rec.droppedFrames.get() > 0)
    }

    @Test fun recording_splits_after_ten_minutes_of_timestamps() = runBlocking {
        val results = mutableListOf<SegmentResult>()
        val rec = recorder(results)
        feed(rec, 0, 20)
        // 타임스탬프를 10분 뒤로 건너뛰어 분할을 일으킨다
        feed(rec, RecordingFormat.SEGMENT_MS * 1_000_000 + 1_000_000_000L, 20)
        withTimeout(10_000) { rec.stop().await() }
        assertEquals(2, results.size)
        results.forEach { result ->
            val r = MediaMetadataRetriever().apply { setDataSource(result.file.path) }
            val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            r.release()
            assertTrue("분할 파일 첫 프레임 0 기준 길이 $durationMs", durationMs < 5_000)
        }
    }

    @Test fun stop_without_frames_leaves_no_file() = runBlocking {
        val results = mutableListOf<SegmentResult>()
        val rec = recorder(results)
        withTimeout(5_000) { rec.stop().await() }
        assertTrue(results.isEmpty())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun snapshot_keeps_full_resolution_orientation_and_burns_mask() {
        val grabber = FrameGrabber(egl.eglBaseContext)
        val f = frame(0)
        val bitmap = grabber.renderOnThisThread(f)
        f.release()
        grabber.release()
        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
        assertTrue("위쪽이 밝아야 한다", bitmap.brightnessAt(.5f, .25f) > bitmap.brightnessAt(.5f, .75f) + 40)

        assertEquals(1, burnMasks(bitmap, listOf(topLeftZone, topLeftZone.copy(enabled = false))))
        assertEquals(0, bitmap.brightnessAt(.25f, .25f))
        assertTrue(bitmap.brightnessAt(.75f, .25f) > 180)
    }

    @Test fun account_b_cannot_see_account_a_files() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.media()
        fun entity(owner: String) = MediaEntity(
            ownerUserId = owner, type = MediaType.IMAGE, source = MediaSource.SNAPSHOT, mac = "M", deviceId = "D",
            cameraName = "C", location = "L", capturedAtUtc = 1, sizeBytes = 1, filePath = "/x/$owner.jpg"
        )
        dao.insert(entity("A"))
        dao.insert(entity("B"))
        assertEquals(listOf("A"), dao.observe("A").first().map { it.ownerUserId })
        val aId = dao.observe("A").first().single().id
        assertTrue(dao.byIds(listOf(aId), "B").isEmpty())
        assertEquals(null, dao.observeOne(aId, "B").first())
        db.close()
    }
}
