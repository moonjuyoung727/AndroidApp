package com.example.androidapplication.core.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.common.formatBytes
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.data.LocalPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class StorageBlockedError(message: String) : AppError(message)

data class StorageUsage(val imageBytes: Long, val videoBytes: Long, val freeBytes: Long) {
    val totalBytes get() = imageBytes + videoBytes
}

/** 로컬 미디어의 파일과 Room 기록을 함께 관리한다. */
@Singleton
class MediaLibrary @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: MediaDao,
    private val prefs: LocalPreferences
) {
    private val cleanupLock = Mutex()

    /** 앱 전용 외부 저장소: 권한이 필요 없고 다른 앱에 노출되지 않으며, 앱을 지우면 함께 삭제된다. */
    private val root: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "SecureCam")

    fun cameraDir(mac: String): File = File(root, mac).apply { mkdirs() }
    fun thumbDir(mac: String): File = File(cameraDir(mac), ".thumbs").apply { mkdirs() }
    fun freeBytes(): Long = root.apply { mkdirs() }.usableSpace

    fun observe(owner: String): Flow<List<MediaEntity>> = dao.observe(owner).flowOn(Dispatchers.IO)
    fun observeOne(id: Long, owner: String): Flow<MediaEntity?> = dao.observeOne(id, owner).flowOn(Dispatchers.IO)

    suspend fun register(item: MediaEntity): MediaEntity = item.copy(id = dao.insert(item))

    suspend fun findEventMedia(eventId: String, owner: String, type: MediaType): MediaEntity? =
        dao.findEvent(eventId, owner, type)?.takeIf { File(it.filePath).exists() }

    /** 저장 전에 부른다. 공간이나 상한 때문에 저장할 수 없으면 안내 문구와 함께 예외를 던진다. */
    suspend fun ensureCanSave(minFreeBytes: Long) {
        if (freeBytes() < minFreeBytes) {
            throw StorageBlockedError("기기 저장 공간이 부족합니다. (${formatBytes(minFreeBytes)} 이상 필요)")
        }
        val settings = prefs.localStorageNow()
        if (settings.deleteOldestOverCap) enforcePolicy()
        else if (StoragePolicy.isBlockedByCap(dao.totalBytes(), settings)) {
            throw StorageBlockedError("저장 용량 상한에 도달했습니다. 파일을 정리하거나 설정에서 상한을 변경해 주세요.")
        }
    }

    /** 녹화 중 주기 확인. 계속할 수 없으면 사유를 돌려준다. */
    suspend fun recordingStopReason(currentSessionUnsavedBytes: Long): String? {
        if (freeBytes() < StoragePolicy.MIN_FREE_WHILE_RECORDING) return "저장 공간이 부족하여 녹화를 종료했습니다."
        val settings = prefs.localStorageNow()
        if (settings.deleteOldestOverCap) {
            enforcePolicy(extraBytes = currentSessionUnsavedBytes)
            return null
        }
        val total = dao.totalBytes() + currentSessionUnsavedBytes
        return if (StoragePolicy.isBlockedByCap(total, settings)) "저장 용량 상한에 도달하여 녹화를 종료했습니다." else null
    }

    suspend fun delete(ids: Collection<Long>, owner: String) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        removeEntities(dao.byIds(ids.toList(), owner))
    }

    suspend fun deleteAll(owner: String) = withContext(Dispatchers.IO) { removeEntities(dao.listOwner(owner)) }

    private suspend fun removeEntities(items: List<MediaEntity>) {
        items.forEach {
            File(it.filePath).delete()
            it.thumbnailPath?.let { path -> File(path).delete() }
        }
        items.map { it.id }.chunked(500).forEach { dao.delete(it) }
    }

    /** 저장 미디어 화면 진입 시 Room과 실제 파일을 대조한다. */
    suspend fun reconcile(owner: String) = withContext(Dispatchers.IO) {
        val missing = dao.listOwner(owner).filterNot { File(it.filePath).exists() }
        missing.forEach { it.thumbnailPath?.let { path -> File(path).delete() } }
        missing.map { it.id }.chunked(500).forEach { dao.delete(it) }
    }

    /** 앱 시작 시 비정상 종료로 남은 작성 중 파일을 지운다. */
    fun cleanTempFiles() {
        root.walkTopDown().filter { it.isFile && it.name.endsWith(".tmp") }.forEach { it.delete() }
    }

    suspend fun enforcePolicy(extraBytes: Long = 0) = cleanupLock.withLock {
        withContext(Dispatchers.IO) {
            val settings = prefs.localStorageNow()
            val entities = dao.listAll()
            val adjusted = settings.copy(capBytes = if (settings.capBytes > 0) (settings.capBytes - extraBytes).coerceAtLeast(1) else 0)
            val ids = StoragePolicy.filesToDelete(
                entities.map { StoredFile(it.id, it.capturedAtUtc, it.sizeBytes) }, adjusted, System.currentTimeMillis()
            )
            removeEntities(entities.filter { it.id in ids })
        }
    }

    fun usage(items: List<MediaEntity>): StorageUsage = StorageUsage(
        items.filter { it.type == MediaType.IMAGE }.sumOf { it.sizeBytes },
        items.filter { it.type == MediaType.VIDEO }.sumOf { it.sizeBytes },
        freeBytes()
    )

    fun writeThumbnail(bitmap: Bitmap, mac: String, baseName: String): String? = runCatching {
        val scale = THUMB_MAX.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        } else bitmap
        val file = File(thumbDir(mac), "$baseName.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (scaled !== bitmap) scaled.recycle()
        file.path
    }.getOrNull()

    fun videoThumbnail(video: File, mac: String): String? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.path)
            val frame = retriever.getFrameAtTime(0) ?: return@runCatching null
            writeThumbnail(frame, mac, video.nameWithoutExtension).also { frame.recycle() }
        } finally {
            retriever.release()
        }
    }.getOrNull()

    fun imageSize(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outWidth.coerceAtLeast(0) to options.outHeight.coerceAtLeast(0)
    }

    fun decodeForThumbnail(file: File): Bitmap? = runCatching {
        val (w, h) = imageSize(file)
        val sample = (maxOf(w, h) / THUMB_MAX).coerceAtLeast(1)
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun videoMeta(file: File): Triple<Long, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            )
        } catch (_: Exception) {
            Triple(0, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 공유는 FileProvider로 한다. */
    fun shareIntent(items: List<MediaEntity>): Intent {
        val uris = ArrayList(items.map { fileUri(it) })
        val mime = when {
            items.all { it.type == MediaType.IMAGE } -> "image/*"
            items.all { it.type == MediaType.VIDEO } -> "video/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        return Intent.createChooser(intent.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "공유")
    }

    private fun fileUri(item: MediaEntity): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(item.filePath))

    /** 사용자가 명시적으로 고른 파일만 MediaStore로 복사한다. 성공 건수를 돌려준다. */
    suspend fun exportToGallery(items: List<MediaEntity>): Int = withContext(Dispatchers.IO) {
        items.count { item -> runCatching { exportOne(item) }.isSuccess }
    }

    private fun exportOne(item: MediaEntity) {
        val source = File(item.filePath)
        val isImage = item.type == MediaType.IMAGE
        val mime = if (isImage) "image/jpeg" else "video/mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isImage) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, (if (isImage) "Pictures" else "Movies") + "/SecureCam")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IllegalStateException("MediaStore insert 실패")
            try {
                resolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES),
                "SecureCam"
            ).apply { mkdirs() }
            val target = File(dir, item.fileName)
            source.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        }
    }

    companion object {
        private const val THUMB_MAX = 480
        private const val WORK_NAME = "securecam-storage-cleanup"

        fun scheduleCleanup(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StorageCleanupWorker>(1, TimeUnit.DAYS).build()
            )
        }
    }
}

/** 보관 기간과 상한 정책을 하루 1회 적용한다. */
@HiltWorker
class StorageCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val library: MediaLibrary
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching { library.enforcePolicy() }
        .fold({ Result.success() }, { Result.retry() })
}

/** 서버가 준 미디어 주소를 정리한다. 상대 경로는 서버 기준으로 풀고, 보드 대역으로의 직접 요청은 막는다. */
object MediaUrls {
    fun resolve(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val resolved = ApiConfig.vpnBaseUrl.toHttpUrl().resolve(url) ?: return null
        if (ApiConfig.isInSubnet(resolved.host, ApiConfig.VPN_BOARD_SUBNET)) return null
        return resolved.toString()
    }
}
