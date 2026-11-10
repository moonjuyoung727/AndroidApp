package com.example.androidapplication.core.webrtc

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class StreamState { Idle, Connecting, Connected, Failed }

data class FrameSize(val width: Int, val height: Int)

private const val ICE_GATHERING_TIMEOUT_MS = 2_000L
private const val SDP_TIMEOUT_MS = 10_000L
private const val TAG = "WhepSession"
private const val DISCONNECTED_TIMEOUT_MS = 5_000L
private const val RECONNECT_BASE_DELAY_MS = 2_000L
private const val RECONNECT_MAX_DELAY_MS = 10_000L

/** 재시도 간격. 2초, 4초, 8초, 이후 10초. */
fun reconnectDelayMs(attempt: Int): Long =
    (RECONNECT_BASE_DELAY_MS shl attempt.coerceIn(0, 3)).coerceAtMost(RECONNECT_MAX_DELAY_MS)

/**
 * 취소될 때까지 스트림을 유지한다. 세션이 실패하면 정리한 뒤 간격을 늘려 가며 새로 연다.
 * 재생에 성공하면 간격을 처음부터 다시 잰다. 보드 offline이나 화면 이탈은 호출자가 취소해서 멈춘다.
 */
suspend fun keepStreaming(open: () -> Flow<StreamState>, release: suspend () -> Unit, onRetry: () -> Unit) {
    var attempt = 0
    while (true) {
        open().first { state ->
            if (state == StreamState.Connected) attempt = 0
            state == StreamState.Failed
        }
        release()
        onRetry()
        delay(reconnectDelayMs(attempt++))
    }
}

/** 세션 URL 해석. Location이 상대 경로여도 WHEP URL 기준으로 절대 주소를 만든다. */
fun resolveSessionUrl(whepUrl: String, location: String?): String? =
    location?.takeIf { it.isNotBlank() }?.let { whepUrl.toHttpUrl().resolve(it)?.toString() }

/** disconnected는 일시적일 수 있어 connecting으로 표시한다. */
fun mapPeerState(state: PeerConnection.PeerConnectionState?, current: StreamState): StreamState = when (state) {
    PeerConnection.PeerConnectionState.CONNECTED -> StreamState.Connected
    PeerConnection.PeerConnectionState.FAILED -> StreamState.Failed
    PeerConnection.PeerConnectionState.DISCONNECTED -> StreamState.Connecting
    else -> current
}

@Singleton
class WhepSessionFactory @Inject constructor(
    private val engine: WebRtcEngine,
    @param:Named("app") private val scope: CoroutineScope
) {
    // API 토큰을 영상 서버로 흘리지 않도록 인터셉터 없는 클라이언트를 쓴다 (WHEP 인증 여부 미확정)
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun open(whepUrl: String): WhepSession = WhepSession(engine, http, whepUrl, scope).also { it.start() }
}

/** 수신 전용 WHEP 세션 하나. trickle 없이 offer 전체를 POST 한다. */
class WhepSession internal constructor(
    private val engine: WebRtcEngine,
    private val http: OkHttpClient,
    private val whepUrl: String,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(StreamState.Connecting)
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val _frameSize = MutableStateFlow<FrameSize?>(null)
    val frameSize: StateFlow<FrameSize?> = _frameSize.asStateFlow()

    /** 트랙에는 이 싱크 하나만 붙이고, 렌더러·녹화기·스냅샷은 여기에 붙었다 떨어진다. */
    private val sinks = CopyOnWriteArrayList<VideoSink>()
    private val fanout = VideoSink { frame: VideoFrame ->
        val size = FrameSize(frame.rotatedWidth, frame.rotatedHeight)
        if (_frameSize.value != size) _frameSize.value = size
        sinks.forEach { it.onFrame(frame) }
    }

    private var peer: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var sessionUrl: String? = null
    private val iceGathered = CompletableDeferred<Unit>()
    private var disconnectTimeout: Job? = null

    @Volatile private var closed = false
    @Volatile private var muted = true
    @Volatile private var volume = 1.0

    fun addSink(sink: VideoSink) {
        sinks.addIfAbsent(sink)
    }

    fun removeSink(sink: VideoSink) {
        sinks.remove(sink)
    }

    /** 음소거가 기본이다. 볼륨은 0.0~1.0. */
    fun setAudio(muted: Boolean, volume: Double) {
        this.muted = muted
        this.volume = volume
        scope.launch(engine.dispatcher) { applyAudio() }
    }

    private fun applyAudio() {
        if (closed) return
        audioTrack?.setEnabled(!muted)
        audioTrack?.setVolume(volume * 10.0) // WebRTC 볼륨 범위는 0~10
    }

    internal fun start() {
        scope.launch(engine.dispatcher) {
            try {
                connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "WHEP 연결 실패", e)
                if (!closed) _state.value = StreamState.Failed
            }
        }
    }

    private suspend fun connect() {
        val pc = createPeer() ?: throw IllegalStateException("PeerConnection 생성 실패")
        peer = pc
        val init = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, init)
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, init)

        val offer = createOffer(pc)
        setLocal(pc, offer)
        withTimeoutOrNull(ICE_GATHERING_TIMEOUT_MS) { iceGathered.await() }
        if (closed) return

        val sdp = pc.localDescription?.description ?: offer.description
        val (answer, location) = withContext(Dispatchers.IO) { postOffer(sdp) }

        // 응답을 기다리는 사이 화면을 벗어났으면 받은 세션도 바로 삭제한다
        if (closed) {
            location?.let(::deleteSession)
            return
        }
        sessionUrl = location
        setRemote(pc, SessionDescription(SessionDescription.Type.ANSWER, answer))
    }

    private fun createPeer(): PeerConnection? {
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }
        return engine.factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (closed) return
                _state.value = mapPeerState(newState, _state.value)
                disconnectTimeout?.cancel()
                // disconnected가 스스로 복구되지 않고 이어지면 실패로 보고 다시 연결한다
                if (newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                    disconnectTimeout = scope.launch(engine.dispatcher) {
                        delay(DISCONNECTED_TIMEOUT_MS)
                        if (!closed) _state.value = StreamState.Failed
                    }
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) iceGathered.complete(Unit)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() ?: return
                scope.launch(engine.dispatcher) { attach(track) }
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
        })
    }

    private fun attach(track: MediaStreamTrack) {
        if (closed) return
        when (track) {
            is VideoTrack -> {
                videoTrack = track
                track.addSink(fanout)
            }
            is AudioTrack -> {
                audioTrack = track
                applyAudio()
            }
        }
    }

    private fun postOffer(sdp: String): Pair<String, String?> {
        val request = Request.Builder()
            .url(whepUrl)
            .post(sdp.toRequestBody("application/sdp".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("WHEP 요청 실패 (${response.code})")
            return response.body.string() to resolveSessionUrl(whepUrl, response.header("Location"))
        }
    }

    private fun deleteSession(url: String) {
        scope.launch(Dispatchers.IO + NonCancellable) {
            runCatching { http.newCall(Request.Builder().url(url).delete().build()).execute().close() }
        }
    }

    /** 멱등. 트랙과 PeerConnection을 정리하고 세션 URL에 DELETE를 보낸다. */
    fun close() {
        if (closed) return
        closed = true
        sinks.clear()
        scope.launch(engine.dispatcher + NonCancellable) {
            runCatching { videoTrack?.removeSink(fanout) }
            videoTrack = null
            audioTrack = null
            runCatching { peer?.dispose() }
            peer = null
            sessionUrl?.let(::deleteSession)
            sessionUrl = null
        }
    }

    private suspend fun createOffer(pc: PeerConnection): SessionDescription = withTimeout(SDP_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) = cont.resume(desc)
                override fun onCreateFailure(error: String?) = cont.resumeWithException(IOException(error))
                override fun onSetSuccess() = Unit
                override fun onSetFailure(error: String?) = Unit
            }, MediaConstraints())
        }
    }

    private suspend fun setLocal(pc: PeerConnection, desc: SessionDescription) = setDescription { pc.setLocalDescription(it, desc) }
    private suspend fun setRemote(pc: PeerConnection, desc: SessionDescription) = setDescription { pc.setRemoteDescription(it, desc) }

    private suspend fun setDescription(block: (SdpObserver) -> Unit) = withTimeout(SDP_TIMEOUT_MS) {
        suspendCancellableCoroutine<Unit> { cont ->
            block(object : SdpObserver {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) = cont.resumeWithException(IOException(error))
                override fun onCreateSuccess(desc: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            })
        }
    }
}
