package com.example.androidapplication.core.webrtc

import android.content.Context
import com.example.androidapplication.core.data.Camera
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** 앱 전체에서 하나만 쓰는 WebRTC 팩토리와 EGL 컨텍스트. */
@Singleton
class WebRtcEngine @Inject constructor(@param:ApplicationContext private val context: Context) {
    /** PeerConnection을 만들고 닫는 호출은 전부 이 스레드에서 한다 (닫기가 오래 걸려도 UI를 막지 않는다). */
    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { Thread(it, "webrtc-control") }
        .asCoroutineDispatcher()

    private val egl: EglBase by lazy { EglBase.create() }
    val eglContext: EglBase.Context get() = egl.eglBaseContext

    val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        // 수신 전용이라 마이크를 쓰지 않는다
        val audio = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()
        PeerConnectionFactory.builder()
            // networkIgnoreMask = 0: VPN 어댑터를 무시하지 않는다 (서버가 VPN 안에만 있다)
            .setOptions(PeerConnectionFactory.Options().apply { networkIgnoreMask = 0 })
            .setAudioDeviceModule(audio)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .createPeerConnectionFactory()
    }
}

enum class StreamQuality(val label: String) { Main("고화질"), Sub("저화질") }

/** 주소가 없으면 null. */
interface StreamUrlResolver {
    fun resolve(camera: Camera, quality: StreamQuality): String?
    /** APP-F-016 지원 화질 목록. */
    fun qualities(camera: Camera): List<StreamQuality>
}

/** 서버가 준 주소만 쓴다. 주소를 직접 조합하지 않는다. */
@Singleton
class ServerStreamUrlResolver @Inject constructor() : StreamUrlResolver {
    override fun resolve(camera: Camera, quality: StreamQuality): String? =
        (if (quality == StreamQuality.Main) camera.webrtcUrl else camera.webrtcUrlSub)?.takeIf { it.isNotBlank() }

    override fun qualities(camera: Camera): List<StreamQuality> = StreamQuality.entries
}
