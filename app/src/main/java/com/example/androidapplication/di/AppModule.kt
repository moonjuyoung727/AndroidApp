package com.example.androidapplication.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.androidapplication.core.config.ApiConfig
import com.example.androidapplication.core.data.PrivacyZoneDao
import com.example.androidapplication.core.data.PrivacyZoneRepository
import com.example.androidapplication.core.data.PrivacyZoneRepositoryImpl
import com.example.androidapplication.core.media.AppDatabase
import com.example.androidapplication.core.media.MediaDao
import com.example.androidapplication.core.network.AccountApi
import com.example.androidapplication.core.network.AppJson
import com.example.androidapplication.core.network.AuthInterceptor
import com.example.androidapplication.core.network.ConnectivityInterceptor
import com.example.androidapplication.core.network.ConnectivityReporter
import com.example.androidapplication.core.network.DeviceApi
import com.example.androidapplication.core.network.EventApi
import com.example.androidapplication.core.network.PrivacyZoneApi
import com.example.androidapplication.core.network.PublicApi
import com.example.androidapplication.core.network.SettingsApi
import com.example.androidapplication.core.network.SubscriptionApi
import com.example.androidapplication.core.network.TokenProvider
import com.example.androidapplication.core.network.VpnApi
import com.example.androidapplication.core.session.SessionManager
import com.example.androidapplication.core.vpn.VpnMonitor
import com.example.androidapplication.core.webrtc.StreamUrlResolver
import com.example.androidapplication.core.webrtc.ServerStreamUrlResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore("session")
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @Named("app")
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton @Named("session")
    fun sessionStore(@ApplicationContext context: Context): DataStore<Preferences> = context.sessionStore

    @Provides @Singleton @Named("settings")
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsStore

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "securecam.db").build()

    @Provides fun mediaDao(db: AppDatabase): MediaDao = db.media()
    @Provides fun privacyZoneDao(db: AppDatabase): PrivacyZoneDao = db.privacyZones()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds abstract fun tokenProvider(impl: SessionManager): TokenProvider
    @Binds abstract fun connectivityReporter(impl: VpnMonitor): ConnectivityReporter
    @Binds abstract fun privacyZones(impl: PrivacyZoneRepositoryImpl): PrivacyZoneRepository
    @Binds abstract fun streamUrls(impl: ServerStreamUrlResolver): StreamUrlResolver
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun okHttp(tokens: TokenProvider, reporter: ConnectivityReporter): OkHttpClient {
        val publicHost = ApiConfig.publicBaseUrl.toHttpUrl().host
        val vpnHost = ApiConfig.vpnBaseUrl.toHttpUrl().host
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(ConnectivityInterceptor(reporter, vpnHost))
            .addInterceptor(AuthInterceptor(tokens, setOf(publicHost, vpnHost)))
            .build()
    }

    private fun retrofit(client: OkHttpClient, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton @Named("public")
    fun publicRetrofit(client: OkHttpClient): Retrofit = retrofit(client, ApiConfig.publicBaseUrl)

    @Provides @Singleton @Named("vpn")
    fun vpnRetrofit(client: OkHttpClient): Retrofit = retrofit(client, ApiConfig.vpnBaseUrl)

    @Provides @Singleton fun publicApi(@Named("public") r: Retrofit): PublicApi = r.create(PublicApi::class.java)
    @Provides @Singleton fun accountApi(@Named("vpn") r: Retrofit): AccountApi = r.create(AccountApi::class.java)
    @Provides @Singleton fun deviceApi(@Named("vpn") r: Retrofit): DeviceApi = r.create(DeviceApi::class.java)
    @Provides @Singleton fun privacyApi(@Named("vpn") r: Retrofit): PrivacyZoneApi = r.create(PrivacyZoneApi::class.java)
    @Provides @Singleton fun eventApi(@Named("vpn") r: Retrofit): EventApi = r.create(EventApi::class.java)
    @Provides @Singleton fun vpnApi(@Named("vpn") r: Retrofit): VpnApi = r.create(VpnApi::class.java)
    @Provides @Singleton fun settingsApi(@Named("vpn") r: Retrofit): SettingsApi = r.create(SettingsApi::class.java)
    @Provides @Singleton fun subscriptionApi(@Named("vpn") r: Retrofit): SubscriptionApi = r.create(SubscriptionApi::class.java)
}
