package com.example.androidapplication.core.media

import android.graphics.Rect
import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.data.PrivacyZone
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor

data class LocalStorageSettings(
    /** 0이면 제한 없음. */
    val retentionDays: Int = 0,
    /** 0이면 제한 없음. */
    val capBytes: Long = DEFAULT_CAP,
    val deleteOldestOverCap: Boolean = false
) {
    companion object {
        const val GB = 1L shl 30
        const val DEFAULT_CAP = 5 * GB
        val RETENTION_OPTIONS = listOf(0, 7, 14, 30, 60, 90)
        val CAP_OPTIONS = listOf(GB, 2 * GB, 5 * GB, 10 * GB, 0L)
    }
}

data class StoredFile(val id: Long, val capturedAtUtc: Long, val sizeBytes: Long)

/** 보관 정책 계산. 파일 시스템과 분리해 단위 테스트한다. */
object StoragePolicy {
    const val MIN_FREE_TO_START = 500L * 1024 * 1024
    const val MIN_FREE_WHILE_RECORDING = 200L * 1024 * 1024
    private const val DAY_MS = 86_400_000L

    /** 보관 기간이 지난 파일, 그 다음 상한을 넘긴 만큼 오래된 파일 순으로 지울 id. */
    fun filesToDelete(files: List<StoredFile>, settings: LocalStorageSettings, nowMs: Long): Set<Long> {
        val delete = linkedSetOf<Long>()
        if (settings.retentionDays > 0) {
            val cutoff = nowMs - settings.retentionDays * DAY_MS
            files.filter { it.capturedAtUtc < cutoff }.mapTo(delete) { it.id }
        }
        if (settings.capBytes > 0 && settings.deleteOldestOverCap) {
            val remaining = files.filterNot { it.id in delete }.sortedBy { it.capturedAtUtc }
            var total = remaining.sumOf { it.sizeBytes }
            for (file in remaining) {
                if (total <= settings.capBytes) break
                delete += file.id
                total -= file.sizeBytes
            }
        }
        return delete
    }

    /** 상한에 도달해 저장을 막아야 하는가. 오래된 파일 삭제가 켜져 있으면 막지 않는다. */
    fun isBlockedByCap(totalBytes: Long, settings: LocalStorageSettings): Boolean =
        settings.capBytes > 0 && !settings.deleteOldestOverCap && totalBytes >= settings.capBytes
}

/** 파일 이름 생성. */
object MediaFileNames {
    private val UNSAFE = Regex("[^A-Za-z0-9_\\-]")

    fun capture(deviceId: String, at: Instant, extension: String, exists: (String) -> Boolean): String =
        unique("${deviceId.replace(UNSAFE, "_")}_${KstTime.fileStamp(at)}", extension, exists)

    fun event(eventId: String, at: Instant, extension: String): String =
        "event_${eventId.replace(UNSAFE, "_")}_${KstTime.compactStamp(at)}.$extension"

    fun unique(base: String, extension: String, exists: (String) -> Boolean): String {
        var name = "$base.$extension"
        var suffix = 1
        while (exists(name)) name = "${base}_${suffix++}.$extension"
        return name
    }
}

/** 비율 좌표의 프라이버시 존을 픽셀로 바꾼다. 가장자리가 새지 않도록 바깥쪽으로 올림한다. */
object MaskGeometry {
    data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    fun active(zones: List<PrivacyZone>): List<PrivacyZone> = zones.filter { it.enabled }

    fun toPixels(zone: PrivacyZone, frameWidth: Int, frameHeight: Int): PixelRect {
        val left = floor(zone.x * frameWidth).toInt().coerceIn(0, frameWidth)
        val top = floor(zone.y * frameHeight).toInt().coerceIn(0, frameHeight)
        val right = ceil((zone.x + zone.width) * frameWidth).toInt().coerceIn(0, frameWidth)
        val bottom = ceil((zone.y + zone.height) * frameHeight).toInt().coerceIn(0, frameHeight)
        return PixelRect(left, top, right, bottom)
    }

    /** glScissor용 y 뒤집기 (GL 원점은 왼쪽 아래). */
    fun glScissorY(rect: PixelRect, frameHeight: Int): Int = frameHeight - rect.bottom

    fun PixelRect.toRect() = Rect(left, top, right, bottom)
}

/** 인코더 크기는 짝수, 비트레이트는 해상도 기반. */
object RecordingFormat {
    const val SEGMENT_MS = 10 * 60 * 1000L
    const val FRAME_RATE = 30
    const val I_FRAME_INTERVAL = 2
    private const val MIN_BITRATE = 1_000_000
    private const val MAX_BITRATE = 12_000_000

    fun evenSize(value: Int): Int = (value and 1.inv()).coerceAtLeast(2)

    fun bitrate(width: Int, height: Int): Int = (width * height * 3).coerceIn(MIN_BITRATE, MAX_BITRATE)
}
