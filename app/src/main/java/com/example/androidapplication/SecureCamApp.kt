package com.example.androidapplication

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.androidapplication.core.media.MediaLibrary
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class SecureCamApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var mediaLibrary: MediaLibrary
    @Inject lateinit var okHttp: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // 프로세스 시작 시점에는 진행 중인 녹화가 없으므로 남은 .tmp는 모두 비정상 종료의 잔재다
        Thread { runCatching { mediaLibrary.cleanTempFiles() } }.start()
        MediaLibrary.scheduleCleanup(this)
    }

    // 썸네일과 이벤트 스냅샷도 인증된 클라이언트로 받는다
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp })) }
        .build()
}
