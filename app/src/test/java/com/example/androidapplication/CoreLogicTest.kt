package com.example.androidapplication

import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.common.pollLoop
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.config.PrivacyZoneLimits
import com.example.androidapplication.core.data.Camera
import com.example.androidapplication.core.data.EventItem
import com.example.androidapplication.core.data.EventTypes
import com.example.androidapplication.core.data.toEvent
import com.example.androidapplication.core.common.AppError
import com.example.androidapplication.core.data.LIST_LOAD_FAILED_MESSAGE
import com.example.androidapplication.core.data.ListSource
import com.example.androidapplication.core.data.ListStore
import com.example.androidapplication.core.data.PrivacyZone
import com.example.androidapplication.core.data.PrivacyZoneMapper
import com.example.androidapplication.core.data.VpnDevice
import com.example.androidapplication.core.data.parseDevices
import com.example.androidapplication.core.data.parseVpnConnections
import com.example.androidapplication.core.data.RecoveryState
import com.example.androidapplication.core.designsystem.ZoomMath
import com.example.androidapplication.core.designsystem.ZoomView
import com.example.androidapplication.core.media.LocalStorageSettings
import com.example.androidapplication.core.media.MediaFileNames
import com.example.androidapplication.core.media.RecordingFormat
import com.example.androidapplication.core.media.StoragePolicy
import com.example.androidapplication.core.media.StoredFile
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.network.PrivacyZoneDto
import com.example.androidapplication.core.network.ZoneCoordinatesDto
import com.example.androidapplication.core.vpn.isClientVpnConnected
import com.example.androidapplication.core.vpn.recheckWhileOffline
import com.example.androidapplication.core.webrtc.ServerStreamUrlResolver
import com.example.androidapplication.core.webrtc.StreamQuality
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.isStreamFailedOut
import com.example.androidapplication.core.webrtc.keepStreaming
import com.example.androidapplication.core.webrtc.mapPeerState
import com.example.androidapplication.core.webrtc.reconnectDelayMs
import com.example.androidapplication.core.webrtc.resolveSessionUrl
import com.example.androidapplication.feature.dashboard.sortVpnIssuesFirst
import com.example.androidapplication.feature.multiview.channelLayout
import com.example.androidapplication.feature.multiview.matchesCamera
import com.example.androidapplication.feature.settings.ZoneDraft
import com.example.androidapplication.core.common.ApiError
import com.example.androidapplication.core.data.ZoneApply
import com.example.androidapplication.core.data.ZoneApplyStatus
import com.example.androidapplication.core.data.ZoneApplyTrack
import com.example.androidapplication.core.data.ZoneSaveErrors
import com.example.androidapplication.core.network.PrivacyZonesRequest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.PeerConnection
import java.time.Instant

class MacTest {
    @Test fun normalize_accepts_any_format() {
        assertEquals("001A2B3C4D5E", Mac.normalize("00:1a:2B-3c:4d:5e"))
        assertEquals("", Mac.normalize(null))
    }

    @Test fun format_adds_colons_or_keeps_invalid_value() {
        assertEquals("00:1A:2B:3C:4D:5E", Mac.format("001a2b3c4d5e"))
        assertEquals("abc", Mac.format("abc"))
    }

    @Test fun device_id_uses_last_four() = assertEquals("CAM-4D5E", Mac.deviceIdFrom("00:1A:2B:3C:4D:5E"))

    @Test fun input_is_formatted_and_capped() {
        assertEquals("AA:B", Mac.formatInput("aab"))
        assertEquals("AA:BB:CC:DD:EE:FF", Mac.formatInput("aabbccddeeff0011"))
    }
}

class KstTimeTest {
    @Test fun utc_with_z_is_shown_in_kst() =
        assertEquals("2026-08-12 20:51:32", KstTime.format("2026-08-12T11:51:32Z"))

    @Test fun missing_zone_is_read_as_utc() {
        assertEquals("2026-08-12 20:51:32", KstTime.format("2026-08-12T11:51:32"))
        assertEquals("2026-08-12 20:51:32", KstTime.format("2026-08-12 11:51:32"))
    }

    @Test fun offsets_are_respected() {
        assertEquals("2026-08-12 11:51:32", KstTime.format("2026-08-12T11:51:32+09:00"))
        assertEquals("2026-08-12 11:51:32", KstTime.format("2026-08-12T11:51:32+0900"))
    }

    @Test fun day_rolls_over_in_kst() = assertEquals("2026-01-01 00:30:00", KstTime.format("2025-12-31T15:30:00Z"))

    @Test fun empty_and_unparsable_values() {
        assertEquals("기록 없음", KstTime.formatLastSeen(null))
        assertEquals("-", KstTime.format(""))
        assertEquals("not-a-date", KstTime.format("not-a-date"))
    }

    @Test fun range_uses_kst_today() {
        val now = Instant.parse("2026-03-31T16:00:00Z") // KST 04-01 01:00
        assertEquals("2026-03-26" to "2026-04-01", KstTime.range(6, now))
    }

    // ICD I4 0.1 #6: KST 하루를 UTC 시각으로. 밀리초는 0이어도 붙인다
    @Test fun kst_day_is_sent_as_utc_instants() {
        assertEquals("2026-11-23T15:00:00.000Z", KstTime.dayStartUtc("2026-11-24"))
        assertEquals("2026-11-24T14:59:59.999Z", KstTime.dayEndUtc("2026-11-24"))
    }

    @Test fun kst_day_boundary_splits_events() {
        val start = Instant.parse(KstTime.dayStartUtc("2026-11-24"))
        val end = Instant.parse(KstTime.dayEndUtc("2026-11-24"))
        val kst0010 = Instant.parse("2026-11-23T15:10:00Z") // KST 11-24 00:10
        val kstPrev2350 = Instant.parse("2026-11-23T14:50:00Z") // KST 11-23 23:50
        assertTrue(kst0010 in start..end)
        assertFalse(kstPrev2350 in start..end)
    }

    @Test fun durations() {
        assertEquals("3일 12시간 24분", KstTime.formatDuration(3 * 86_400L + 12 * 3_600 + 24 * 60))
        assertEquals("-", KstTime.formatDuration(0))
        assertEquals("1시간 05분", KstTime.formatUptime(3_900))
        assertEquals("-", KstTime.formatUptime(null))
        assertEquals("01:01:01", KstTime.formatElapsed(3_661_000))
    }
}

class ValidatorsTest {
    @Test fun email() {
        assertNull(Validators.email("a@b.co"))
        assertNotNull(Validators.email("a@b"))
        assertNotNull(Validators.email("x".repeat(250) + "@b.co"))
    }

    @Test fun password_rules() {
        assertNull(Validators.password("Abcd123!"))
        assertNotNull(Validators.password("short1!"))
        assertNull(Validators.password("한글 비밀번호~입니다"))
        assertNull(Validators.password("a".repeat(128)))
        assertNotNull(Validators.password("a".repeat(129)))
    }

    @Test fun password_input_only_limits_length() {
        assertEquals("a b한c!1~", Validators.filterPasswordInput("a b한c!1~"))
        assertEquals(128, Validators.filterPasswordInput("a".repeat(200)).length)
    }

    @Test fun confirm() {
        assertNull(Validators.passwordConfirm("a", "a"))
        assertNotNull(Validators.passwordConfirm("a", "b"))
    }

    @Test fun camera_mac_format_and_duplicate() {
        assertNull(Validators.cameraMac("AA:BB:CC:DD:EE:FF", emptyList()))
        assertNotNull(Validators.cameraMac("AA:BB", emptyList()))
        assertNotNull(Validators.cameraMac("AA:BB:CC:DD:EE:FF", listOf("aabbccddeeff")))
    }

    @Test fun camera_name() {
        assertNull(Validators.cameraName("출입구-1 (A)_b"))
        assertNotNull(Validators.cameraName(""))
        assertNotNull(Validators.cameraName("a".repeat(16)))
        assertNotNull(Validators.cameraName("이름!"))
    }

    @Test fun memo_limit() {
        assertNull(Validators.memo("a".repeat(500)))
        assertNotNull(Validators.memo("a".repeat(501)))
    }
}

class ZoomMathTest {
    @Test fun scale_is_limited() {
        assertEquals(4f, ZoomMath.zoomAround(ZoomView(), 9f, 0.5f, 0.5f).scale)
        assertEquals(1f, ZoomMath.zoomAround(ZoomView(2f), 0.1f, 0.5f, 0.5f).scale)
    }

    @Test fun focal_point_stays_put() {
        val view = ZoomMath.zoomAround(ZoomView(), 2f, 0.25f, 0.25f)
        assertEquals(0.25f, 0.25f * view.scale + view.x, 1e-5f)
        assertEquals(0.25f, 0.25f * view.scale + view.y, 1e-5f)
    }

    @Test fun video_always_fills_stage() {
        val view = ZoomMath.pan(ZoomView(2f, -0.5f, -0.5f), 5f, -5f)
        assertEquals(0f, view.x)
        assertEquals(-1f, view.y)
    }

    @Test fun no_pan_when_not_zoomed() = assertEquals(ZoomView(), ZoomMath.pan(ZoomView(), 0.3f, 0.3f))

    @Test fun buttons_step_by_half() {
        assertEquals(1.5f, ZoomMath.zoomIn(ZoomView()).scale)
        assertEquals(1f, ZoomMath.zoomOut(ZoomView(1.5f, -0.25f, -0.25f)).scale)
    }

    @Test fun double_tap_toggles() {
        val zoomed = ZoomMath.doubleTap(ZoomView(), 0.5f, 0.5f)
        assertEquals(2f, zoomed.scale)
        assertEquals(ZoomView(), ZoomMath.doubleTap(zoomed, 0.5f, 0.5f))
    }

    @Test fun pinch_moves_with_centroid() {
        val v = ZoomMath.gesture(ZoomView(2f, -0.5f, -0.5f), 0.5f, 0.5f, 1f, 0.1f, 0f)
        assertEquals(-0.4f, v.x, 1e-5f)
    }
}

class PrivacyZoneTest {
    private val dto = PrivacyZoneDto("z1", "문", true, "#000", ZoneCoordinatesDto(0.1f, 0.2f, 0.4f, 0.8f))

    @Test fun server_to_app() {
        val zone = PrivacyZoneMapper.fromDto(dto)
        assertEquals(0.1f, zone.x, 1e-5f)
        assertEquals(0.3f, zone.width, 1e-5f)
        assertEquals(0.6f, zone.height, 1e-5f)
        assertTrue(zone.enabled)
    }

    @Test fun app_to_server_round_trips() {
        val body = PrivacyZoneMapper.toBody(PrivacyZoneMapper.fromDto(dto))
        assertEquals(0.4f, body.coordinates.x2, 1e-5f)
        assertEquals(0.8f, body.coordinates.y2, 1e-5f)
    }

    @Test fun draft_enforces_min_size_and_max_count() {
        var id = 0
        fun add(list: List<PrivacyZone>) = ZoneDraft.tryAdd(list, 0f, 0f, 0.5f, 0.5f) { "${id++}" }
        assertNull(ZoneDraft.tryAdd(emptyList(), 0f, 0f, 0.02f, 0.5f) { "x" })
        assertEquals(8, PrivacyZoneLimits.MAX_ZONES)
        assertEquals(30, PrivacyZoneLimits.NAME_MAX)
        var zones = emptyList<PrivacyZone>()
        repeat(PrivacyZoneLimits.MAX_ZONES) { zones = add(zones)!! }
        assertEquals((1..PrivacyZoneLimits.MAX_ZONES).map { "영역 $it" }, zones.map { it.name })
        assertNull(add(zones))
    }

    @Test fun draft_normalizes_reverse_drag_and_clamps() {
        val z = ZoneDraft.tryAdd(emptyList(), 1.5f, 0.6f, 0.5f, -0.2f) { "x" }!!.single()
        assertEquals(0.5f, z.x, 1e-5f)
        assertEquals(0f, z.y, 1e-5f)
        assertEquals(0.5f, z.width, 1e-5f)
    }

    @Test fun body_keeps_zone_id_and_clamps_rounded_coordinates() {
        val zone = PrivacyZone("local-uuid", "끝", 0.7f, 0.9000001f, 0.3000001f, 0.1f, enabled = true)
        val body = PrivacyZoneMapper.toBody(zone)
        assertEquals("local-uuid", body.zoneId)
        assertTrue(body.coordinates.x2 <= 1f)
        assertTrue(body.coordinates.y2 <= 1f)
        assertEquals(1f, body.coordinates.x2, 0f)
        assertEquals(0.9f, body.coordinates.y1, 0f)
        assertEquals(0f, PrivacyZoneMapper.ratio(-0.0000004), 0f)
    }

    @Test fun request_sends_only_allowed_fields_and_omits_null_color() {
        val zone = PrivacyZone("z1", "문", 0.1f, 0.2f, 0.3f, 0.4f, enabled = false)
        val json = AppJson.encodeToString(PrivacyZonesRequest.serializer(), PrivacyZonesRequest(listOf(PrivacyZoneMapper.toBody(zone))))
        val item = AppJson.parseToJsonElement(json).jsonObject["zones"]!!.jsonArray.single().jsonObject
        assertEquals(setOf("zone_id", "zone_name", "is_active", "coordinates"), item.keys)
        assertEquals("""{"zones":[]}""", AppJson.encodeToString(PrivacyZonesRequest.serializer(), PrivacyZonesRequest(emptyList())))
    }

    @Test fun validate_checks_name_and_size_before_save() {
        val ok = PrivacyZone("a", "문", 0f, 0f, 0.5f, 0.5f, enabled = true)
        assertNull(ZoneDraft.validate(listOf(ok)))
        assertNull(ZoneDraft.validate(emptyList()))
        assertNotNull(ZoneDraft.validate(listOf(ok.copy(name = " "))))
        assertNotNull(ZoneDraft.validate(listOf(ok.copy(name = "가".repeat(31)))))
        assertNotNull(ZoneDraft.validate(listOf(ok.copy(width = 0.02f))))
        assertNotNull(ZoneDraft.validate(List(9) { ok.copy(id = "$it") }))
    }
}

class ZoneApplyStatusTest {
    private val saved = Instant.parse("2026-11-18T00:00:00Z")
    private fun cam(config: Int?, applied: Int?, online: Boolean) = Camera(
        deviceId = "d", mac = "AABBCCDDEEFF", name = "", location = "",
        status = if (online) "online" else "offline", configVersion = config, appliedConfigVersion = applied
    )

    @Test fun judge_by_equality_only() {
        assertEquals(ZoneApplyStatus.Applied, ZoneApply.judge(cam(3, 3, online = false)))
        assertEquals(ZoneApplyStatus.Pending, ZoneApply.judge(cam(3, 2, online = true)))
        assertEquals(ZoneApplyStatus.Offline, ZoneApply.judge(cam(3, 2, online = false)))
        // 서버 버전이 낮아져도 같지 않으면 적용 대기
        assertEquals(ZoneApplyStatus.Pending, ZoneApply.judge(cam(1, 5, online = true)))
        assertNull(ZoneApply.judge(cam(null, null, online = true)))
        assertNull(ZoneApply.judge(null))
    }

    @Test fun list_from_before_save_is_not_trusted() {
        val track = ZoneApplyTrack(saved)
        val before = saved.minusSeconds(1)
        assertEquals(ZoneApplyStatus.Pending, ZoneApply.status(cam(2, 2, online = true), before, track))
        assertEquals(ZoneApplyStatus.Offline, ZoneApply.status(cam(2, 2, online = false), before, track))
        assertEquals(ZoneApplyStatus.Applied, ZoneApply.status(cam(3, 3, online = true), saved.plusSeconds(3), track))
    }

    @Test fun delayed_only_when_online_and_time_passed() {
        val expired = ZoneApplyTrack(saved, expired = true)
        val after = saved.plusSeconds(16)
        assertEquals(ZoneApplyStatus.Delayed, ZoneApply.status(cam(3, 2, online = true), after, expired))
        assertEquals(ZoneApplyStatus.Offline, ZoneApply.status(cam(3, 2, online = false), after, expired))
        // 지연 뒤에도 두 버전이 같아지면 적용됨
        assertEquals(ZoneApplyStatus.Applied, ZoneApply.status(cam(3, 3, online = true), after, expired))
    }
}

class ZoneSaveErrorsTest {
    private val zones = listOf(
        PrivacyZone("a", "문", 0f, 0f, 0.5f, 0.5f, enabled = true),
        PrivacyZone("b", "창", 0f, 0f, 0.5f, 0.5f, enabled = true)
    )

    @Test fun bad_request_lists_reasons_per_zone() {
        val details = """[{"field":"zones[1].zone_name","message":"30자 이하"}]"""
        val text = ZoneSaveErrors.describe(ApiError(400, "BAD_REQUEST", "잘못된 값", details), zones)
        assertEquals("잘못된 값\n• 2번 영역(창) 이름: 30자 이하", text)
    }

    // 서버 errorHandler 형식: details = [{ index, field, reason }]
    @Test fun server_details_combine_index_and_field() {
        val details = """[{"index":1,"field":"coordinates","reason":"x1 must be less than x2"},{"index":0,"field":"zone_name","reason":"too long"}]"""
        assertEquals(
            listOf("2번 영역(창) 좌표: x1 must be less than x2", "1번 영역(문) 이름: too long"),
            ZoneSaveErrors.reasons(details, zones)
        )
        assertEquals(listOf("3번 영역: unknown field"), ZoneSaveErrors.reasons("""[{"index":2,"reason":"unknown field"}]""", zones))
        assertEquals(listOf("영역 목록: too many"), ZoneSaveErrors.reasons("""[{"field":"zones","reason":"too many"}]""", zones))
    }

    @Test fun reasons_accept_object_map_and_zone_id() {
        assertEquals(listOf("1번 영역(문) 좌표 x2: ≤ 1"), ZoneSaveErrors.reasons("""{"zones.0.coordinates.x2":"≤ 1"}""", zones))
        assertEquals(listOf("2번 영역(창): 중복"), ZoneSaveErrors.reasons("""[{"zone_id":"b","reason":"중복"}]""", zones))
        assertEquals(emptyList<String>(), ZoneSaveErrors.reasons(null, zones))
    }

    @Test fun forbidden_and_not_found_have_own_messages() {
        assertEquals(ZoneSaveErrors.FORBIDDEN_MESSAGE, ZoneSaveErrors.describe(ApiError(403, null, "x"), zones))
        assertEquals(ZoneSaveErrors.NOT_FOUND_MESSAGE, ZoneSaveErrors.describe(ApiError(404, null, "x"), zones))
    }
}

class StoragePolicyTest {
    private val day = 86_400_000L
    private val now = 100 * day

    private fun file(id: Long, ageDays: Int, size: Long) = StoredFile(id, now - ageDays * day, size)

    @Test fun retention_deletes_only_expired() {
        val ids = StoragePolicy.filesToDelete(
            listOf(file(1, 8, 1), file(2, 6, 1)),
            LocalStorageSettings(retentionDays = 7, capBytes = 0), now
        )
        assertEquals(setOf(1L), ids)
    }

    @Test fun unlimited_retention_keeps_everything() =
        assertTrue(StoragePolicy.filesToDelete(listOf(file(1, 999, 1)), LocalStorageSettings(0, 0), now).isEmpty())

    @Test fun cap_deletes_oldest_until_under_limit() {
        val ids = StoragePolicy.filesToDelete(
            listOf(file(1, 1, 40), file(2, 3, 40), file(3, 2, 40)),
            LocalStorageSettings(capBytes = 50, deleteOldestOverCap = true), now
        )
        assertEquals(setOf(2L, 3L), ids)
    }

    @Test fun cap_without_auto_delete_blocks_instead() {
        val settings = LocalStorageSettings(capBytes = 50, deleteOldestOverCap = false)
        assertTrue(StoragePolicy.filesToDelete(listOf(file(1, 1, 100)), settings, now).isEmpty())
        assertTrue(StoragePolicy.isBlockedByCap(50, settings))
        assertFalse(StoragePolicy.isBlockedByCap(49, settings))
        assertFalse(StoragePolicy.isBlockedByCap(99, settings.copy(deleteOldestOverCap = true)))
        assertFalse(StoragePolicy.isBlockedByCap(99, settings.copy(capBytes = 0)))
    }

    @Test fun recording_format() {
        assertEquals(1920, RecordingFormat.evenSize(1921))
        assertEquals(1_000_000, RecordingFormat.bitrate(160, 120))
        assertEquals(12_000_000, RecordingFormat.bitrate(7680, 4320))
    }
}

class FileNameTest {
    private val at = Instant.parse("2026-08-12T11:51:32Z")

    @Test fun capture_name_uses_kst() =
        assertEquals("CAM-4D5E_20260812_205132.jpg", MediaFileNames.capture("CAM-4D5E", at, "jpg") { false })

    @Test fun collisions_get_suffix() {
        val taken = setOf("A_20260812_205132.mp4", "A_20260812_205132_1.mp4")
        assertEquals("A_20260812_205132_2.mp4", MediaFileNames.capture("A", at, "mp4") { it in taken })
    }

    @Test fun device_id_is_sanitized() =
        assertEquals("a_b_20260812_205132.jpg", MediaFileNames.capture("a/b", at, "jpg") { false })

    @Test fun event_name() = assertEquals("event_77_20260812205132.mp4", MediaFileNames.event("77", at, "mp4"))
}

class NetworkParsingTest {
    private fun json(text: String) = AppJson.decodeFromString<JsonElement>(text)

    @Test fun devices_array_or_envelope() {
        val item = """{"mac":"aa:bb:cc:dd:ee:ff","name":"A"}"""
        assertEquals(1, parseDevices(json("[$item]")).size)
        assertEquals(1, parseDevices(json("""{"devices":[$item]}""")).size)
        assertEquals(1, parseDevices(json("""{"items":[$item]}""")).size)
        assertTrue(parseDevices(json("{}")).isEmpty())
    }

    @Test fun missing_fields_get_defaults() {
        val camera = parseDevices(json("""[{"mac":"AABBCCDDEEFF","status":null,"extra":1}]""")).single()
        assertEquals("CAM-EEFF", camera.deviceId)
        assertEquals("offline", camera.status)
        assertEquals("", camera.name)
    }

    @Test fun vpn_connections_envelope() {
        val list = parseVpnConnections(json("""{"connections":[{"mac":"aabbccddeeff","device_name":"A","vpn_status":"error"}]}"""))
        assertEquals("AABBCCDDEEFF", list.single().mac)
        assertEquals(RecoveryState.Failed, list.single().recovery)
    }

    @Test fun numeric_event_id_is_read_as_string() {
        val dto = AppJson.decodeFromString<com.example.androidapplication.core.network.EventDto>("""{"event_id":12,"event_type":"system"}""")
        assertEquals("12", dto.eventId)
    }

    // ICD I4 2.3.1 응답: device_id·detail·object_class를 읽고 내부 컬럼(event_key 등)은 무시한다
    @Test fun event_reads_i4_fields() {
        val dto = AppJson.decodeFromString<com.example.androidapplication.core.network.EventDto>(
            """{"event_id":"e1","device_id":"CAM-4D5E","device_name":"현관","event_type":"motion",
               "object_class":"person","detail":"사람 감지","event_key":"k1","created_at":"x","location":null}"""
        )
        val event = dto.toEvent()
        assertEquals("CAM-4D5E", event.deviceId)
        assertEquals("person", event.objectClass)
        assertEquals("사람 감지", event.description)
        assertEquals("", event.mac)
        assertNull(event.location)
    }

    @Test fun event_name_falls_back_to_device_id() {
        val dto = AppJson.decodeFromString<com.example.androidapplication.core.network.EventDto>("""{"event_id":"e1","device_id":"CAM-4D5E"}""")
        assertEquals("CAM-4D5E", dto.toEvent().cameraName)
    }

    // 예전 버전이 저장한 내비게이션 인자 JSON(새 필드 없음)도 복원되어야 한다
    @Test fun old_event_argument_json_still_decodes() {
        val old = """{"id":"e1","occurredAt":"2026-11-24T01:00:00Z","mac":"AABBCCDDEEFF","cameraName":"A","type":"motion","status":"unconfirmed"}"""
        val event = AppJson.decodeFromString(EventItem.serializer(), old)
        assertEquals("", event.deviceId)
        assertNull(event.objectClass)
        assertEquals("AABBCCDDEEFF", event.mac)
    }

    @Test fun event_types_follow_icd() {
        assertEquals(listOf("motion", "sound", "connection", "system"), EventTypes.server)
        assertEquals("연결 상태", EventTypes.label("connection"))
        assertEquals("시스템", EventTypes.label("system"))
        assertEquals("unknown_type", EventTypes.label("unknown_type"))
    }

    // ICD I4 2.7: device_alerts는 device_id 키. 예전 mac 키 항목은 device_id가 비어 읽힌다
    @Test fun device_alert_uses_device_id_key() {
        val dto = AppJson.decodeFromString<com.example.androidapplication.core.network.NotificationSettingsDto>(
            """{"device_alerts":[{"device_id":"CAM-4D5E","enabled":false},{"mac":"AABBCCDDEEFF","enabled":false}]}"""
        )
        assertEquals(listOf("CAM-4D5E", ""), dto.deviceAlerts.map { it.deviceId })
        val sent = AppJson.encodeToString(
            com.example.androidapplication.core.network.DeviceAlertDto.serializer(),
            com.example.androidapplication.core.network.DeviceAlertDto("CAM-4D5E", false)
        )
        assertEquals("""{"device_id":"CAM-4D5E","enabled":false}""", sent)
    }

    @Test fun push_token_request_sends_platform() {
        val body = AppJson.encodeToString(
            com.example.androidapplication.core.network.PushTokenRequest.serializer(),
            com.example.androidapplication.core.network.PushTokenRequest("t")
        )
        assertEquals("""{"token":"t","platform":"android"}""", body)
    }
}

class VpnAndStreamTest {
    @Test fun client_outside_subnet_is_not_connected() {
        assertFalse(isClientVpnConnected(true, "192.168.0.5", true))
        assertTrue(isClientVpnConnected(true, "10.0.2.7", true))
        assertFalse(isClientVpnConnected(true, "10.0.2.7", false))
        assertTrue(ApiConfig.isInSubnet("10.0.1.9", ApiConfig.VPN_BOARD_SUBNET))
    }

    @Test fun session_url_is_resolved_against_whep_url() {
        val whep = "http://10.0.0.1:8889/cam/whep"
        assertEquals("http://10.0.0.1:8889/cam/whep/abc", resolveSessionUrl(whep, "/cam/whep/abc"))
        assertEquals("http://x.test/s", resolveSessionUrl(whep, "http://x.test/s"))
        assertNull(resolveSessionUrl(whep, null))
    }

    @Test fun disconnected_is_shown_as_connecting() {
        assertEquals(StreamState.Connecting, mapPeerState(PeerConnection.PeerConnectionState.DISCONNECTED, StreamState.Connected))
        assertEquals(StreamState.Connected, mapPeerState(PeerConnection.PeerConnectionState.CONNECTED, StreamState.Connecting))
        assertEquals(StreamState.Failed, mapPeerState(PeerConnection.PeerConnectionState.FAILED, StreamState.Connected))
    }

    @Test fun stream_urls_come_only_from_server() {
        val resolver = ServerStreamUrlResolver()
        val main = "http://10.0.0.100:8889/CAM-E099/whep"
        val sub = "http://10.0.0.100:8889/CAM-E099_sub/whep"
        val camera = Camera(deviceId = "CAM-E099", mac = "MAC", name = "n", location = "l", webrtcUrl = main, webrtcUrlSub = sub)
        assertEquals(main, resolver.resolve(camera, StreamQuality.Main))
        assertEquals(sub, resolver.resolve(camera, StreamQuality.Sub))

        val noStreams = camera.copy(webrtcUrl = null, webrtcUrlSub = " ")
        assertNull(resolver.resolve(noStreams, StreamQuality.Main))
        assertNull(resolver.resolve(noStreams, StreamQuality.Sub))
        assertEquals(listOf(StreamQuality.Main, StreamQuality.Sub), resolver.qualities(noStreams))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun keep_streaming_waits_while_vpn_is_down() = runTest {
        val vpnUsable = MutableStateFlow(false)
        val sessions = mutableListOf<MutableStateFlow<StreamState>>()
        val waiting = mutableListOf<Boolean>()
        val job = launch {
            keepStreaming(
                open = { MutableStateFlow<StreamState>(StreamState.Connecting).also(sessions::add) },
                release = {},
                onRetry = {},
                network = vpnUsable,
                onWaitingNetwork = { waiting += it }
            )
        }
        runCurrent()
        assertEquals(0, sessions.size)
        assertEquals(listOf(true), waiting)

        vpnUsable.value = true
        runCurrent()
        assertEquals(1, sessions.size)
        assertEquals(listOf(true, false), waiting)

        // 재연결 대기 중에 VPN이 끊기면 다음 시도도 VPN 복구까지 미룬다
        vpnUsable.value = false
        sessions.last().value = StreamState.Failed
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, sessions.size)

        vpnUsable.value = true
        runCurrent()
        assertEquals(2, sessions.size)
        job.cancel()
    }

    @Test fun reconnect_delay_grows_to_ten_seconds() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 10_000L, 10_000L), (0..4).map(::reconnectDelayMs))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun keep_streaming_releases_then_retries_with_backoff() = runTest {
        val sessions = mutableListOf<MutableStateFlow<StreamState>>()
        var released = 0
        var retries = 0
        val job = launch {
            keepStreaming(
                open = { MutableStateFlow<StreamState>(StreamState.Connecting).also(sessions::add) },
                release = { released++ },
                onRetry = { retries++ }
            )
        }
        suspend fun failThenExpectNextAfter(delayMs: Long) {
            val opened = sessions.size
            sessions.last().value = StreamState.Failed
            runCurrent()
            assertEquals(opened, released)
            assertEquals(opened, retries)
            advanceTimeBy(delayMs - 1)
            runCurrent()
            assertEquals(opened, sessions.size)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(opened + 1, sessions.size)
        }

        runCurrent()
        assertEquals(1, sessions.size)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, retries)

        failThenExpectNextAfter(2_000)
        failThenExpectNextAfter(4_000)

        // 재생에 성공하면 간격을 처음부터 다시 잰다
        sessions.last().value = StreamState.Connected
        runCurrent()
        failThenExpectNextAfter(2_000)
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun stream_shows_failed_after_five_consecutive_failures() = runTest {
        val sessions = mutableListOf<MutableStateFlow<StreamState>>()
        val failures = mutableListOf<Int>()
        val vpnUsable = MutableStateFlow(true)
        val job = launch {
            keepStreaming(
                open = { MutableStateFlow<StreamState>(StreamState.Connecting).also(sessions::add) },
                release = {},
                onRetry = {},
                network = vpnUsable,
                onFailures = { failures += it }
            )
        }
        suspend fun fail() {
            sessions.last().value = StreamState.Failed
            advanceTimeBy(10_000)
            runCurrent()
        }
        runCurrent()
        repeat(4) { fail() }
        assertFalse(isStreamFailedOut(failures.last()))
        fail()
        assertTrue(isStreamFailedOut(failures.last()))
        assertEquals(6, sessions.size) // 실패로 표시해도 재시도는 계속한다

        // 재생에 성공하면 처음부터 센다
        sessions.last().value = StreamState.Connected
        runCurrent()
        assertEquals(0, failures.last())

        // VPN 끊김 중의 실패는 회복 후 다시 센다
        repeat(5) { fail() }
        vpnUsable.value = false
        fail()
        assertEquals(6, failures.last())
        vpnUsable.value = true
        runCurrent()
        assertEquals(0, failures.last())
        job.cancel()
    }

    @Test fun layouts_follow_orientation() {
        assertEquals(2 to 1, channelLayout(2, landscape = true))
        assertEquals(1 to 2, channelLayout(2, landscape = false))
        assertEquals(3 to 2, channelLayout(6, landscape = true))
        assertEquals(2 to 3, channelLayout(6, landscape = false))
        assertEquals(2 to 2, channelLayout(4, landscape = true))
    }

    private val camera = Camera("CAM", "AABBCCDDEEFF", "정문 카메라", "1층", status = "online")

    @Test fun search_ignores_mac_colons() {
        assertTrue(matchesCamera(camera, "cc:dd", "all"))
        assertTrue(matchesCamera(camera, "ccdd", "all"))
        assertTrue(matchesCamera(camera, "정문", "online"))
        assertFalse(matchesCamera(camera, "정문", "offline"))
        assertFalse(matchesCamera(camera, "후문", "all"))
    }

    @Test fun vpn_sort_puts_issues_first() {
        fun d(status: String) = VpnDevice("m", "n", null, status, null, null, null, null, null, null)
        val sorted = sortVpnIssuesFirst(listOf(d("connected"), d("disconnected"), d("error")))
        assertEquals(listOf("error", "disconnected", "connected"), sorted.map { it.vpnStatus })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PollingTest {
    @Test fun next_wait_starts_after_request_finishes() = runTest {
        val calls = mutableListOf<Long>()
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            pollLoop(5_000, repeat = true) { first ->
                calls += testScheduler.currentTime
                if (first) gate.await() // 첫 요청이 오래 걸린다
                true
            }
        }
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf(0L), calls) // 응답 전에는 다음 요청을 보내지 않는다
        gate.complete(Unit)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(0L, 25_000L), calls)
        job.cancel()
    }

    @Test fun vpn_offline_pauses_auto_polls_and_recovery_polls_immediately() = runTest {
        val online = MutableStateFlow(false)
        val calls = mutableListOf<Pair<Long, Boolean>>()
        val job = launch {
            pollLoop(5_000, repeat = true, online = online) { first -> calls += testScheduler.currentTime to first; true }
        }
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf(0L to true), calls) // 첫 조회만 보내고 반복 조회는 멈춘다
        online.value = true
        runCurrent()
        assertEquals(listOf(0L to true, 30_000L to false), calls) // 다음 주기를 기다리지 않는다
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(35_000L to false, calls.last())
        job.cancel()
    }

    @Test fun vpn_recheck_runs_every_10s_only_while_offline() = runTest {
        val usable = MutableStateFlow(true)
        val checks = mutableListOf<Long>()
        val job = launch { recheckWhileOffline(usable) { checks += testScheduler.currentTime } }
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(checks.isEmpty())
        usable.value = false
        runCurrent()
        advanceTimeBy(25_000)
        runCurrent()
        assertEquals(listOf(40_000L, 50_000L), checks)
        usable.value = true
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(2, checks.size) // 회복되면 멈춘다
        job.cancel()
    }

    @Test fun disabled_auto_refresh_polls_once() = runTest {
        var count = 0
        pollLoop(5_000, repeat = false) { count++; true }
        assertEquals(1, count)
    }

    @Test fun disabled_auto_refresh_polls_once_more_after_vpn_recovery() = runTest {
        val online = MutableStateFlow(true)
        val calls = mutableListOf<Pair<Long, Boolean>>()
        val job = launch {
            pollLoop(5_000, repeat = false, online = online) { first -> calls += testScheduler.currentTime to first; true }
        }
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(listOf(0L to true), calls) // 켜져 있지 않으면 반복하지 않는다
        online.value = false
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, calls.size) // 끊긴 동안은 보내지 않는다
        online.value = true
        runCurrent()
        assertEquals(listOf(0L to true, 40_000L to false), calls) // 복구되면 바로 한 번
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, calls.size) // 그 뒤로는 다시 멈춘다
        job.cancel()
    }

    @Test fun disabled_auto_refresh_entered_while_offline_polls_on_recovery() = runTest {
        val online = MutableStateFlow(false)
        val calls = mutableListOf<Long>()
        val job = launch { pollLoop(5_000, repeat = false, online = online) { calls += testScheduler.currentTime; true } }
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf(0L), calls) // 진입 시 첫 조회는 VPN과 상관없이 보낸다
        online.value = true
        runCurrent()
        assertEquals(listOf(0L, 20_000L), calls)
        job.cancel()
    }

    @Test fun failed_poll_retries_after_15s_then_returns_to_interval() = runTest {
        val calls = mutableListOf<Long>()
        val results = ArrayDeque(listOf(true, false, false, true))
        val job = launch {
            pollLoop(5_000, repeat = true) {
                calls += testScheduler.currentTime
                results.removeFirstOrNull() ?: true
            }
        }
        advanceTimeBy(45_001)
        runCurrent()
        // 성공 → 5초, 실패 → 15초, 실패 → 15초, 성공 → 5초
        assertEquals(listOf(0L, 5_000L, 20_000L, 35_000L, 40_000L, 45_000L), calls)
        job.cancel()
    }

    @Test fun failed_retry_waits_for_vpn_then_runs_immediately() = runTest {
        val online = MutableStateFlow(true)
        val calls = mutableListOf<Long>()
        val job = launch {
            pollLoop(5_000, repeat = true, online = online) { calls += testScheduler.currentTime; calls.size > 1 }
        }
        runCurrent()
        online.value = false // 실패 A: VPN 끊김
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(listOf(0L), calls)
        online.value = true
        runCurrent()
        assertEquals(listOf(0L, 40_000L), calls)
        job.cancel()
    }

    @Test fun auto_refresh_failed_retry_cut_short_by_vpn_recovery() = runTest {
        val online = MutableStateFlow(true)
        val calls = mutableListOf<Pair<Long, Boolean>>()
        val job = launch {
            pollLoop(5_000, repeat = true, online = online) { first -> calls += testScheduler.currentTime to first; calls.size > 1 }
        }
        runCurrent() // 첫 조회 실패, 15초 재시도 대기 시작
        advanceTimeBy(2_000)
        online.value = false
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(0L to true), calls)
        online.value = true // 4초에 복구
        runCurrent()
        assertEquals(listOf(0L to true, 4_000L to false), calls) // 15초를 기다리지 않는다
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(9_000L to false, calls.last()) // 성공 뒤에는 원래 주기로
        job.cancel()
    }

    @Test fun one_shot_failed_retry_cut_short_by_vpn_recovery() = runTest {
        val online = MutableStateFlow(false)
        val calls = mutableListOf<Long>()
        val job = launch {
            pollLoop(5_000, repeat = false, online = online) { calls += testScheduler.currentTime; calls.size > 1 }
        }
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(0L), calls) // 끊긴 상태에서 첫 조회 실패
        online.value = true // 3초에 복구
        runCurrent()
        assertEquals(listOf(0L, 3_000L), calls) // 아직 못 불러왔으니 바로 조회
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, calls.size) // 성공한 뒤에는 멈춘다
        job.cancel()
    }

    @Test fun failed_retry_without_vpn_change_still_waits_15s() = runTest {
        val online = MutableStateFlow(true)
        val calls = mutableListOf<Long>()
        val job = launch {
            pollLoop(5_000, repeat = false, online = online) { calls += testScheduler.currentTime; calls.size > 1 }
        }
        advanceTimeBy(15_001)
        runCurrent()
        assertEquals(listOf(0L, 15_000L), calls)
        job.cancel()
    }

    @Test fun one_shot_poll_retries_every_15s_until_success() = runTest {
        val calls = mutableListOf<Long>()
        pollLoop(5_000, repeat = false) { calls += testScheduler.currentTime; calls.size == 3 }
        assertEquals(listOf(0L, 15_000L, 30_000L), calls)
    }

    @Test fun forced_refresh_cancels_in_flight_fetch() = runTest {
        val store = ListStore<Int>()
        val stale = async { store.refresh { awaitCancellation() } }
        runCurrent()
        store.refresh(force = true) { listOf(2) }
        stale.await() // 밀려난 호출은 오류 없이 끝난다
        assertEquals(listOf(2), store.state.value.items)
        assertEquals(ListSource.Server, store.state.value.source)
        assertFalse(store.state.value.isFetching)
    }

    @Test fun overlapping_refresh_is_skipped() = runTest {
        val store = ListStore<Int>()
        val gate = CompletableDeferred<Unit>()
        var fetches = 0
        val first = async { store.refresh { fetches++; gate.await(); listOf(1) } }
        runCurrent()
        store.refresh { fetches++; listOf(2) }
        assertEquals(1, fetches)
        gate.complete(Unit)
        first.await()
        assertEquals(listOf(1), store.state.value.items)
        assertEquals(ListSource.Server, store.state.value.source)
    }

    @Test fun failure_marks_stale_and_keeps_items() = runTest {
        val store = ListStore<Int>()
        store.refresh { listOf(1) }
        runCatching { store.refresh { throw IllegalStateException() } }
        assertEquals(ListSource.Stale, store.state.value.source)
        assertEquals(listOf(1), store.state.value.items)
        assertFalse(store.state.value.isFetching)
        assertEquals(LIST_LOAD_FAILED_MESSAGE, store.state.value.error)
    }

    @Test fun failure_before_any_data_is_error_with_message() = runTest {
        val store = ListStore<Int>()
        runCatching { store.refresh { throw AppError("VPN 연결을 확인하세요.") } }
        assertEquals(ListSource.Error, store.state.value.source)
        assertEquals("VPN 연결을 확인하세요.", store.state.value.error)
        assertTrue(store.state.value.items.isEmpty())

        store.refresh { listOf(1) }
        assertEquals(ListSource.Server, store.state.value.source)
        assertNull(store.state.value.error)
    }
}
