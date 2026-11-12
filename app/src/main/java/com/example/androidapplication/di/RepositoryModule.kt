package com.example.androidapplication.di

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
import com.example.androidapplication.core.vpn.ClientVpnProbe
import com.example.androidapplication.core.vpn.EmbeddedWireGuardController
import com.example.androidapplication.core.vpn.RemoteClientVpnProbe
import com.example.androidapplication.core.vpn.VpnTunnelController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** 모든 빌드가 서버 구현을 쓴다. 서버에 닿지 못하면 빈 화면과 안내를 보여 준다. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun auth(impl: RemoteAuthRepository): AuthRepository
    @Binds abstract fun device(impl: RemoteDeviceRepository): DeviceRepository
    @Binds abstract fun event(impl: RemoteEventRepository): EventRepository
    @Binds abstract fun vpn(impl: RemoteVpnRepository): VpnRepository
    @Binds abstract fun settings(impl: RemoteSettingsRepository): SettingsRepository
    @Binds abstract fun cloud(impl: RemoteCloudStorageRepository): CloudStorageRepository
    @Binds abstract fun subscription(impl: RemoteSubscriptionRepository): SubscriptionRepository
    @Binds abstract fun probe(impl: RemoteClientVpnProbe): ClientVpnProbe
    @Binds abstract fun tunnel(impl: EmbeddedWireGuardController): VpnTunnelController
}
