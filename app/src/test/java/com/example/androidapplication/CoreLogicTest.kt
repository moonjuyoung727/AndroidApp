package com.example.androidapplication

import com.example.androidapplication.core.common.KstTime
import com.example.androidapplication.core.common.Mac
import com.example.androidapplication.core.common.Validators
import com.example.androidapplication.core.common.pollLoop
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.data.Camera
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
import com.example.androidapplication.core.media.MaskGeometry
import com.example.androidapplication.core.media.MediaFileNames
import com.example.androidapplication.core.media.RecordingFormat
import com.example.androidapplication.core.media.StoragePolicy
import com.example.androidapplication.core.media.StoredFile
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.network.PrivacyZoneDto
import com.example.androidapplication.core.network.ZoneCoordinatesDto
import com.example.androidapplication.core.vpn.isClientVpnConnected
import com.example.androidapplication.core.webrtc.ServerStreamUrlResolver
import com.example.androidapplication.core.webrtc.StreamQuality
import com.example.androidapplication.core.webrtc.StreamState
import com.example.androidapplication.core.webrtc.keepStreaming
import com.example.androidapplication.core.webrtc.mapPeerState
import com.example.androidapplication.core.webrtc.reconnectDelayMs
import com.example.androidapplication.core.webrtc.resolveSessionUrl
import com.example.androidapplication.feature.dashboard.sortVpnIssuesFirst
import com.example.androidapplication.feature.multiview.channelLayout
import com.example.androidapplication.feature.multiview.matchesCamera
import com.example.androidapplication.feature.settings.ZoneDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
        assertNotNull(Validators.password("한글비밀번호입니다"))
        assertNotNull(Validators.password("a".repeat(65)))
    }

    @Test fun password_input_blocks_disallowed_chars() =
        assertEquals("abc!1", Validators.filterPasswordInput("a b한c!1~"))

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

    private fun zone(x: Float, y: Float, w: Float, h: Float, on: Boolean = true) = PrivacyZone("i", "n", x, y, w, h, on)

    @Test fun mask_rounds_outward_and_stays_in_frame() {
        val rect = MaskGeometry.toPixels(zone(0.101f, 0.5f, 0.2f, 0.6f), 1000, 100)
        assertEquals(101, rect.left)
        assertEquals(301, rect.right)
        assertEquals(50, rect.top)
        assertEquals(100, rect.bottom)
    }

    @Test fun gl_scissor_flips_y() {
        val rect = MaskGeometry.PixelRect(0, 10, 50, 40)
        assertEquals(60, MaskGeometry.glScissorY(rect, 100))
    }

    @Test fun inactive_zones_are_not_burned() =
        assertEquals(1, MaskGeometry.active(listOf(zone(0f, 0f, .1f, .1f), zone(0f, 0f, .1f, .1f, false))).size)

    @Test fun draft_enforces_min_size_and_max_count() {
        var id = 0
        fun add(list: List<PrivacyZone>) = ZoneDraft.tryAdd(list, 0f, 0f, 0.5f, 0.5f) { "${id++}" }
        assertNull(ZoneDraft.tryAdd(emptyList(), 0f, 0f, 0.02f, 0.5f) { "x" })
        var zones = emptyList<PrivacyZone>()
        repeat(4) { zones = add(zones)!! }
        assertEquals(listOf("영역 1", "영역 2", "영역 3", "영역 4"), zones.map { it.name })
        assertNull(add(zones))
    }

    @Test fun draft_normalizes_reverse_drag_and_clamps() {
        val z = ZoneDraft.tryAdd(emptyList(), 1.5f, 0.6f, 0.5f, -0.2f) { "x" }!!.single()
        assertEquals(0.5f, z.x, 1e-5f)
        assertEquals(0f, z.y, 1e-5f)
        assertEquals(0.5f, z.width, 1e-5f)
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

    @Test fun disabled_auto_refresh_polls_once() = runTest {
        var count = 0
        pollLoop(5_000, repeat = false) { count++ }
        assertEquals(1, count)
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
    }
}
