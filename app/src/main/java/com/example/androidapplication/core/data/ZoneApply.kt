package com.example.androidapplication.core.data

import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.common.ApiErrorCode
import com.example.androidapplication.core.network.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 프라이버시 존 적용 상태 (ICD 2.6, UC1-E7) */
enum class ZoneApplyStatus(val label: String) {
    Applied("적용됨"),
    Pending("적용 대기 중"),
    Offline("보드 오프라인 (재연결 시 적용)"),
    /** 저장 후 15초 안에 적용되지 않음. 보드가 online일 때만 */
    Delayed("적용 확인 지연")
}

/** 저장 직후의 추적. [expired]는 재조회 시간(15초)이 지났는지. */
data class ZoneApplyTrack(val savedAt: Instant, val expired: Boolean = false)

object ZoneApply {
    const val POLL_INTERVAL_MS = 2_500L
    const val POLL_LIMIT_MS = 15_000L

    /**
     * 두 버전이 같으면 적용됨, 다르면 보드 상태로 대기·오프라인. 같은지로만 비교한다 (서버 버전이 낮아질 수 있음).
     * 서버가 버전을 보내지 않으면 판정할 수 없어 null.
     */
    fun judge(camera: Camera?): ZoneApplyStatus? {
        val config = camera?.configVersion ?: return null
        val applied = camera.appliedConfigVersion ?: return null
        return when {
            applied == config -> ZoneApplyStatus.Applied
            camera.isOnline -> ZoneApplyStatus.Pending
            else -> ZoneApplyStatus.Offline
        }
    }

    /**
     * [track]이 있으면 저장 이후에 받은 목록([fetchedAt] > savedAt)만 판정에 쓴다.
     * 저장 전 목록의 "적용됨"이 남아 보이지 않도록, 그 전까지는 대기(오프라인이면 오프라인)로 본다.
     */
    fun status(camera: Camera?, fetchedAt: Instant?, track: ZoneApplyTrack?): ZoneApplyStatus? {
        if (judge(camera) == null) return null
        val fresh = track == null || (fetchedAt != null && fetchedAt.isAfter(track.savedAt))
        val status = when {
            fresh -> judge(camera)
            camera?.isOnline == true -> ZoneApplyStatus.Pending
            else -> ZoneApplyStatus.Offline
        }
        return if (status == ZoneApplyStatus.Pending && track?.expired == true) ZoneApplyStatus.Delayed else status
    }
}

/**
 * 저장 후 적용 상태를 화면끼리 공유한다 (존 편집 화면, 모니터링 화면).
 * 재조회는 [followUp]을 부른 코루틴이 맡으므로 화면을 떠나면 멈춘다.
 * 그 뒤에도 기기 목록이 갱신되어 두 버전이 같아지면 적용됨으로 바뀐다.
 */
@Singleton
class ZoneApplyTracker @Inject constructor(
    private val devices: DeviceRepository,
    @param:Named("app") private val timers: CoroutineScope
) {
    private val tracks = MutableStateFlow<Map<String, ZoneApplyTrack>>(emptyMap())

    fun status(mac: String): Flow<ZoneApplyStatus?> = combine(devices.cameras, tracks) { list, all ->
        val track = all[mac]
        val status = ZoneApply.status(list.items.firstOrNull { it.mac == mac }, list.fetchedAt, track)
        // 시간이 지난 뒤 적용되면 추적을 끝낸다. 이후 다른 변경은 다시 적용 대기부터 보여 준다
        if (track != null && track.expired && status == ZoneApplyStatus.Applied) settle(mac, track.savedAt)
        status
    }.distinctUntilChanged()

    /** 저장에 성공하면 부른다. 즉시 적용 대기로 바꾸고 2.5초 간격으로 최대 15초 기기 목록을 다시 받는다. */
    suspend fun followUp(mac: String) {
        val track = ZoneApplyTrack(Instant.now())
        tracks.update { it + (mac to track) }
        // 화면을 떠나 재조회가 멈춰도 15초가 지나면 지연으로 바뀌어야 하므로 시계는 따로 둔다
        timers.launch {
            delay(ZoneApply.POLL_LIMIT_MS)
            tracks.update { all ->
                val current = all[mac]
                if (current?.savedAt == track.savedAt) all + (mac to current.copy(expired = true)) else all
            }
        }
        withTimeoutOrNull(ZoneApply.POLL_LIMIT_MS) {
            while (true) {
                delay(ZoneApply.POLL_INTERVAL_MS)
                devices.refresh(silent = true, force = true)
                val list = devices.cameras.value
                if (ZoneApply.status(list.items.firstOrNull { it.mac == mac }, list.fetchedAt, track) == ZoneApplyStatus.Applied) {
                    settle(mac, track.savedAt)
                    break
                }
            }
        }
    }

    private fun settle(mac: String, savedAt: Instant) =
        tracks.update { all -> if (all[mac]?.savedAt == savedAt) all - mac else all }
}

/** 저장 실패 안내 (UC1-E2). 400은 details의 항목별 사유를 몇 번째 영역인지와 함께 보여 준다. */
object ZoneSaveErrors {
    const val BAD_REQUEST_MESSAGE = "저장할 수 없는 값이 있습니다."
    const val FORBIDDEN_MESSAGE = "내 보드가 아닙니다. 이 보드의 프라이버시 존을 저장할 수 없습니다."
    const val NOT_FOUND_MESSAGE = "보드를 찾을 수 없습니다."

    private val fieldLabels = mapOf(
        "zone_id" to "ID",
        "zone_name" to "이름",
        "is_active" to "활성화",
        "color" to "색상",
        "coordinates" to "좌표",
        "zones" to "영역 목록"
    )

    /** [zones]는 저장하려던 편집 중 목록 */
    fun describe(error: Throwable, zones: List<PrivacyZone>): String {
        if (error !is ApiError) return error.message ?: ZONE_APPLY_FAILED
        return when (error.kind) {
            ApiErrorCode.FORBIDDEN -> FORBIDDEN_MESSAGE
            ApiErrorCode.NOT_FOUND -> NOT_FOUND_MESSAGE
            ApiErrorCode.BAD_REQUEST -> {
                val reasons = reasons(error.details, zones)
                (listOf(error.message) + reasons.map { "• $it" }).joinToString("\n")
            }
            else -> error.message
        }
    }

    fun reasons(details: String?, zones: List<PrivacyZone>): List<String> {
        if (details.isNullOrBlank()) return emptyList()
        val root = runCatching { AppJson.parseToJsonElement(details) }.getOrElse { return listOf(details) }
        return when (root) {
            is JsonArray -> root.mapNotNull { detail(it, zones) }
            is JsonObject -> if (root.keys.any { it in reasonKeys || it in pathKeys }) {
                listOfNotNull(detail(root, zones))
            } else {
                root.entries.map { (path, reason) -> join(path(JsonPrimitive(path), zones), text(reason)) }
            }
            else -> listOfNotNull(text(root).ifBlank { null })
        }
    }

    private val reasonKeys = listOf("message", "reason", "msg", "error")
    private val pathKeys = listOf("field", "loc", "path", "location")

    private fun detail(element: JsonElement, zones: List<PrivacyZone>): String? {
        if (element !is JsonObject) return text(element).ifBlank { null }
        val reason = reasonKeys.firstNotNullOfOrNull { element[it] }?.let(::text).orEmpty()
        val location = pathKeys.firstNotNullOfOrNull { element[it] }?.let(::location)
        // 서버 형식 { index, field: "coordinates", reason }처럼 위치에 번호가 없으면 index·zone_id로 영역을 찾는다
        val index = location?.index ?: (element["index"] ?: element["zone_index"])?.let { (it as? JsonPrimitive)?.intOrNull }
        val zone = if (index != null) {
            zoneLabel(zones, index)
        } else {
            (element["zone_id"] as? JsonPrimitive)?.contentOrNull?.let { id ->
                zones.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { zoneLabel(zones, it) } ?: id
            }
        }
        val where = listOfNotNull(zone, location?.field?.ifBlank { null }).joinToString(" ")
        return join(where, reason).ifBlank { null }
    }

    private class Location(val index: Int?, val field: String)

    // "zones[2].zone_name", "zones.2.coordinates.x2", ["body", "zones", 2, "zone_name"], "coordinates" 같은 위치를 읽는다
    private fun location(element: JsonElement): Location {
        val parts = when (element) {
            is JsonArray -> element.map { text(it) }
            else -> text(element).split(Regex("[.\\[\\]]+"))
        }.filter { it.isNotBlank() && it != "body" }
        val zonesAt = parts.indexOf("zones")
        val index = if (zonesAt >= 0) parts.getOrNull(zonesAt + 1)?.toIntOrNull() else null
        val fields = if (index != null) parts.drop(zonesAt + 2) else parts
        return Location(index, fields.joinToString(" ") { fieldLabels[it] ?: it })
    }

    private fun path(element: JsonElement, zones: List<PrivacyZone>): String {
        val location = location(element)
        return listOfNotNull(location.index?.let { zoneLabel(zones, it) }, location.field.ifBlank { null }).joinToString(" ")
    }

    private fun zoneLabel(zones: List<PrivacyZone>, index: Int): String =
        zones.getOrNull(index)?.let { "${index + 1}번 영역(${it.name.ifBlank { "이름 없음" }})" } ?: "${index + 1}번 영역"

    private fun text(element: JsonElement): String = when (element) {
        is JsonNull -> ""
        is JsonPrimitive -> element.content
        is JsonArray -> element.joinToString(", ") { text(it) }
        is JsonObject -> element.toString()
    }

    private fun join(where: String, reason: String) = if (where.isBlank()) reason else "$where: $reason"
}
