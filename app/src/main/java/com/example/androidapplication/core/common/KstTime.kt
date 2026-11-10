package com.example.androidapplication.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 서버 시각(UTC ISO 8601)을 읽어 기기 시간대와 관계없이 KST로 표시한다. */
object KstTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    const val LAST_SEEN_EMPTY = "기록 없음"

    private val DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZONE)
    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZONE)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZONE)
    private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZONE)
    private val COMPACT_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZONE)

    private val HAS_ZONE = Regex("([zZ]|[+-]\\d{2}(:?\\d{2})?)$")

    /** 시간대 표시가 없는 문자열도 UTC로 읽는다. 해석할 수 없으면 null. */
    fun parseUtc(value: String?): Instant? {
        val text = value?.trim().orEmpty().replace(' ', 'T')
        if (text.isEmpty()) return null
        return try {
            if (HAS_ZONE.containsMatchIn(text.substringAfter('T', ""))) {
                OffsetDateTime.parse(normalizeOffset(text)).toInstant()
            } else {
                LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // +0900, +09 처럼 콜론이 없는 오프셋도 읽을 수 있게 맞춘다
    private fun normalizeOffset(text: String): String {
        val match = Regex("([+-])(\\d{2})(\\d{2})?$").find(text.substringAfter('T')) ?: return text
        val (sign, hour, minute) = match.destructured
        return text.removeSuffix(match.value) + "$sign$hour:${minute.ifEmpty { "00" }}"
    }

    /** 값이 없으면 [empty], 해석할 수 없는 문자열이면 받은 그대로. */
    fun format(value: String?, empty: String = "-"): String {
        if (value.isNullOrBlank()) return empty
        return parseUtc(value)?.let(DISPLAY::format) ?: value
    }

    fun format(instant: Instant): String = DISPLAY.format(instant)
    fun time(instant: Instant): String = TIME.format(instant)
    fun date(instant: Instant): String = DATE.format(instant)
    fun formatLastSeen(value: String?): String = format(value, LAST_SEEN_EMPTY)
    fun fileStamp(instant: Instant): String = FILE_STAMP.format(instant)
    fun compactStamp(instant: Instant): String = COMPACT_STAMP.format(instant)

    fun today(now: Instant = Instant.now()): LocalDate = now.atZone(ZONE).toLocalDate()

    /** 오늘을 포함해 [daysBack]일 전부터 오늘까지 (양 끝 포함, yyyy-MM-dd). */
    fun range(daysBack: Int, now: Instant = Instant.now()): Pair<String, String> {
        val end = today(now)
        return end.minusDays(daysBack.toLong()).toString() to end.toString()
    }

    /** "3일 12시간 24분". 0 또는 null이면 "-". */
    fun formatDuration(totalSeconds: Long?): String {
        if (totalSeconds == null || totalSeconds <= 0) return "-"
        val days = totalSeconds / 86_400
        val hours = totalSeconds % 86_400 / 3_600
        val minutes = totalSeconds % 3_600 / 60
        return buildList {
            if (days > 0) add("${days}일")
            if (hours > 0) add("${hours}시간")
            add("${minutes}분")
        }.joinToString(" ")
    }

    /** 보드 업타임. 값이 없으면 "-". */
    fun formatUptime(seconds: Long?): String {
        if (seconds == null) return "-"
        val total = seconds.coerceAtLeast(0)
        val days = total / 86_400
        val hours = total % 86_400 / 3_600
        val minutes = (total % 3_600 / 60).toString().padStart(2, '0')
        return if (days > 0) "${days}일 ${hours}시간 ${minutes}분" else "${hours}시간 ${minutes}분"
    }

    /** 녹화 경과 시간 "00:12:34". */
    fun formatElapsed(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
    }
}

fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0).toDouble()
    return when {
        value >= 1L shl 30 -> "%.2f GB".format(value / (1L shl 30))
        value >= 1L shl 20 -> "%.1f MB".format(value / (1L shl 20))
        value >= 1L shl 10 -> "%.0f KB".format(value / (1L shl 10))
        else -> "${bytes.coerceAtLeast(0)} B"
    }
}
