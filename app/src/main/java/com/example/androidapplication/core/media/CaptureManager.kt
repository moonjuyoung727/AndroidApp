package com.example.androidapplication.core.media

import android.graphics.Bitmap
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.PrivacyZone
import com.example.androidapplication.core.data.PrivacyZoneRepository
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.session.SessionManager
import com.example.androidapplication.core.webrtc.WebRtcEngine
import com.example.androidapplication.core.webrtc.WhepSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class CaptureError(message: String) : AppError(message)

private const val SNAPSHOT_TIMEOUT_MS = 3_000L
private const val SNAPSHOT_MIN_FREE = 20L * 1024 * 1024
private const val JPEG_QUALITY = 90
const val ZONES_UNAVAILABLE = "프라이버시 존 정보를 불러오지 못해 저장할 수 없습니다."

@Singleton
class CaptureManager @Inject constructor(
    private val engine: WebRtcEngine,
    private val library: MediaLibrary,
    private val zoneRepository: PrivacyZoneRepository,
    private val sessions: SessionManager,
    private val okHttp: OkHttpClient,
    @param:Named("app") private val scope: CoroutineScope
) {
    private val grabber by lazy { FrameGrabber(engine.eglContext) }

    private fun owner(): String = sessions.session?.userId ?: throw CaptureError("로그인 정보를 확인할 수 없습니다.")

    /** 존 정보를 불러오지 못하면 저장을 막는다 (fail closed). */
    private suspend fun requireZones(mac: String): List<PrivacyZone> = try {
        zoneRepository.current(mac)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        throw CaptureError(ZONES_UNAVAILABLE)
    }

    suspend fun snapshot(session: WhepSession, camera: Camera): MediaEntity {
        val ownerId = owner()
        val zones = requireZones(camera.mac)
        library.ensureCanSave(SNAPSHOT_MIN_FREE)
        val bitmap = grabber.grab(session, SNAPSHOT_TIMEOUT_MS)
            ?: throw CaptureError("영상 프레임을 받지 못했습니다. 잠시 후 다시 시도해 주세요.")
        return withContext(Dispatchers.IO) {
            try {
                val masked = burnMasks(bitmap, zones)
                val now = Instant.now()
                val dir = library.cameraDir(camera.mac)
                val name = MediaFileNames.capture(camera.deviceId, now, "jpg") { File(dir, it).exists() }
                val target = File(dir, name)
                val tmp = File(dir, "$name.tmp")
                try {
                    FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                    if (!tmp.renameTo(target)) throw IOException("rename 실패")
                } catch (e: Exception) {
                    tmp.delete()
                    throw CaptureError("스냅샷 저장에 실패했습니다.")
                }
                library.register(
                    MediaEntity(
                        ownerUserId = ownerId, type = MediaType.IMAGE, source = MediaSource.SNAPSHOT,
                        mac = camera.mac, deviceId = camera.deviceId, cameraName = camera.name, location = camera.location,
                        capturedAtUtc = now.toEpochMilli(), width = bitmap.width, height = bitmap.height,
                        sizeBytes = target.length(), filePath = target.path,
                        thumbnailPath = library.writeThumbnail(bitmap, camera.mac, target.nameWithoutExtension),
                        maskedZoneCount = masked
                    )
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    suspend fun startRecording(session: WhepSession, camera: Camera, onFatal: (Throwable) -> Unit): RecordingHandle {
        val ownerId = owner()
        val zones = requireZones(camera.mac)
        library.ensureCanSave(StoragePolicy.MIN_FREE_TO_START)
        return RecordingHandle(session, camera, ownerId, zones, engine, library, scope, onFatal)
    }

    /** 이벤트 미디어를 인증된 클라이언트로 받아 라이브러리에 EVENT_DOWNLOAD로 등록한다. 실패하면 부분 파일을 지운다. */
    suspend fun downloadEventMedia(
        event: EventItem,
        url: String,
        type: MediaType,
        onProgress: (Float?) -> Unit
    ): MediaEntity = withContext(Dispatchers.IO) {
        val ownerId = owner()
        val resolved = MediaUrls.resolve(url) ?: throw CaptureError("다운로드 주소가 올바르지 않습니다.")
        library.findEventMedia(event.id, ownerId, type)?.let { return@withContext it }
        library.ensureCanSave(SNAPSHOT_MIN_FREE)

        val mac = Mac.normalize(event.mac)
        val at = KstTime.parseUtc(event.occurredAt) ?: Instant.now()
        val name = MediaFileNames.event(event.id, at, if (type == MediaType.IMAGE) "jpg" else "mp4")
        val target = File(library.cameraDir(mac), name)
        val tmp = File(target.path + ".tmp")
        try {
            okHttp.newCall(Request.Builder().url(resolved).build()).execute().use { response ->
                if (!response.isSuccessful) throw CaptureError("미디어 다운로드에 실패했습니다.")
                val total = response.body.contentLength()
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                FileOutputStream(tmp).use { out ->
                    response.body.byteStream().use { input ->
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            read += count
                            onProgress(if (total > 0) read.toFloat() / total else null)
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) throw IOException("rename 실패")
        } catch (e: Exception) {
            tmp.delete()
            if (e is CancellationException || e is CaptureError) throw e
            throw CaptureError("미디어 다운로드에 실패했습니다.")
        }

        val (duration, width, height) = if (type == MediaType.VIDEO) library.videoMeta(target)
        else library.imageSize(target).let { Triple(0L, it.first, it.second) }
        val thumb = if (type == MediaType.VIDEO) library.videoThumbnail(target, mac)
        else library.decodeForThumbnail(target)?.let { bmp ->
            library.writeThumbnail(bmp, mac, target.nameWithoutExtension).also { bmp.recycle() }
        }
        library.register(
            MediaEntity(
                ownerUserId = ownerId, type = type, source = MediaSource.EVENT_DOWNLOAD,
                mac = mac, deviceId = Mac.deviceIdFrom(mac), cameraName = event.cameraName, location = event.location.orEmpty(),
                capturedAtUtc = at.toEpochMilli(), durationMs = duration, width = width, height = height,
                sizeBytes = target.length(), filePath = target.path, thumbnailPath = thumb,
                eventId = event.id, eventType = event.type
            )
        )
    }
}

/** 한 번의 녹화. 10분 분할 파일들을 같은 recordingSessionId로 묶어 등록한다. */
class RecordingHandle internal constructor(
    private val session: WhepSession,
    private val camera: Camera,
    private val ownerId: String,
    initialZones: List<PrivacyZone>,
    engine: WebRtcEngine,
    private val library: MediaLibrary,
    private val scope: CoroutineScope,
    onFatal: (Throwable) -> Unit
) {
    private val sessionId = UUID.randomUUID().toString()
    private val registering = CopyOnWriteArrayList<Job>()
    private val saved = CopyOnWriteArrayList<MediaEntity>()
    val startedAtMs = System.currentTimeMillis()

    private val recorder = VideoRecorder(engine.eglContext, ::nextFile, ::onSegment, onFatal).also {
        it.zones = initialZones
        session.addSink(it)
    }

    val writtenBytes: Long get() = recorder.writtenBytes
    val currentSegmentBytes: Long get() = recorder.currentSegmentBytes
    val droppedFrames: Long get() = recorder.droppedFrames.get()

    fun updateZones(zones: List<PrivacyZone>) {
        recorder.zones = zones
    }

    private fun nextFile(): File {
        val dir = library.cameraDir(camera.mac)
        return File(dir, MediaFileNames.capture(camera.deviceId, Instant.now(), "mp4") {
            File(dir, it).exists() || File(dir, "$it.tmp").exists()
        })
    }

    private fun onSegment(result: SegmentResult) {
        registering += scope.launch(Dispatchers.IO) {
            val entity = library.register(
                MediaEntity(
                    ownerUserId = ownerId, type = MediaType.VIDEO, source = MediaSource.RECORDING,
                    mac = camera.mac, deviceId = camera.deviceId, cameraName = camera.name, location = camera.location,
                    capturedAtUtc = result.startedAtMs, durationMs = result.durationMs,
                    width = result.width, height = result.height, sizeBytes = result.sizeBytes,
                    filePath = result.file.path, thumbnailPath = library.videoThumbnail(result.file, camera.mac),
                    recordingSessionId = sessionId, maskedZoneCount = result.maskedZoneCount
                )
            )
            saved += entity
        }
    }

    /** 파일을 마무리하고 등록까지 끝낸다. 호출 쪽 코루틴이 취소되어도 앱 스코프에서 끝까지 진행한다. */
    suspend fun stop(): List<MediaEntity> {
        session.removeSink(recorder)
        val finish = scope.launch {
            recorder.stop().await()
            registering.joinAll()
        }
        finish.join()
        return saved.toList()
    }
}
