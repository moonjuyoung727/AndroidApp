package com.example.androidapplication.di

import android.content.Context
import com.example.androidapplication.core.data.AuthRepository
import com.example.androidapplication.core.data.CloudStorageRepository
import com.example.androidapplication.core.data.DeviceRepository
import com.example.androidapplication.core.data.EventRepository
import com.example.androidapplication.core.data.RemoteAuthRepository
import com.example.androidapplication.core.data.RemoteCloudStorageRepository
import com.example.androidapplication.core.data.RemoteDeviceRepository
import com.example.androidapplication.core.data.RemoteEventRepository
import com.example.androidapplication.core.data.RemoteSettingsRepository
import com.example.androidapplication.core.data.RemoteSubscriptionRepository
import com.example.androidapplication.core.data.RemoteVpnRepository
import com.example.androidapplication.core.data.SettingsRepository
import com.example.androidapplication.core.data.SubscriptionRepository
import com.example.androidapplication.core.data.VpnRepository
import com.example.androidapplication.core.fake.FakeAuthRepository
import com.example.androidapplication.core.fake.FakeClientVpnProbe
import com.example.androidapplication.core.fake.FakeCloudStorageRepository
import com.example.androidapplication.core.fake.FakeDeviceRepository
import com.example.androidapplication.core.fake.FakeEventRepository
import com.example.androidapplication.core.fake.FakeSettingsRepository
import com.example.androidapplication.core.fake.FakeSubscriptionRepository
import com.example.androidapplication.core.fake.FakeVpnRepository
import com.example.androidapplication.core.vpn.ClientVpnProbe
import com.example.androidapplication.core.vpn.EmbeddedWireGuardController
import com.example.androidapplication.core.vpn.NoOpTunnelController
import com.example.androidapplication.core.vpn.RemoteClientVpnProbe
import com.example.androidapplication.core.vpn.VpnTunnelController
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 로그인 화면의 개발자 스위치로 고른다. 앱을 다시 시작해야 적용된다. */
@Singleton
class DebugDevOptions(context: Context) : DevOptions {
    private val prefs = context.getSharedPreferences("dev_options", Context.MODE_PRIVATE)
    override val available = true
    override var useFake: Boolean
        get() = prefs.getBoolean("use_fake", false)
        set(value) {
            prefs.edit().putBoolean("use_fake", value).commit()
        }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides @Singleton
    fun devOptions(@ApplicationContext context: Context): DevOptions = DebugDevOptions(context)

    private fun <T> pick(dev: DevOptions, fake: Lazy<out T>, remote: Lazy<out T>): T =
        if (dev.useFake) fake.get() else remote.get()

    @Provides @Singleton fun auth(d: DevOptions, f: Lazy<FakeAuthRepository>, r: Lazy<RemoteAuthRepository>): AuthRepository = pick(d, f, r)
    @Provides @Singleton fun device(d: DevOptions, f: Lazy<FakeDeviceRepository>, r: Lazy<RemoteDeviceRepository>): DeviceRepository = pick(d, f, r)
    @Provides @Singleton fun event(d: DevOptions, f: Lazy<FakeEventRepository>, r: Lazy<RemoteEventRepository>): EventRepository = pick(d, f, r)
    @Provides @Singleton fun vpn(d: DevOptions, f: Lazy<FakeVpnRepository>, r: Lazy<RemoteVpnRepository>): VpnRepository = pick(d, f, r)
    @Provides @Singleton fun settings(d: DevOptions, f: Lazy<FakeSettingsRepository>, r: Lazy<RemoteSettingsRepository>): SettingsRepository = pick(d, f, r)
    @Provides @Singleton fun cloud(d: DevOptions, f: Lazy<FakeCloudStorageRepository>, r: Lazy<RemoteCloudStorageRepository>): CloudStorageRepository = pick(d, f, r)
    @Provides @Singleton fun subscription(d: DevOptions, f: Lazy<FakeSubscriptionRepository>, r: Lazy<RemoteSubscriptionRepository>): SubscriptionRepository = pick(d, f, r)
    @Provides @Singleton fun probe(d: DevOptions, f: Lazy<FakeClientVpnProbe>, r: Lazy<RemoteClientVpnProbe>): ClientVpnProbe = pick(d, f, r)
    @Provides @Singleton fun tunnel(d: DevOptions, f: Lazy<NoOpTunnelController>, r: Lazy<EmbeddedWireGuardController>): VpnTunnelController = pick(d, f, r)
}
