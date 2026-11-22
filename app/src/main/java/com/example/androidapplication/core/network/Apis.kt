package com.example.androidapplication.core.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** 로그인 전에 쓰는 API. I2에서는 이 API도 VPN 내부 서버로 나간다. */
interface PublicApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @POST("auth/register") suspend fun register(@Body body: RegisterRequest): RegisterResponse
    @POST("auth/find-id") suspend fun findId(@Body body: FindIdRequest): FindIdResponse
    @POST("auth/reset-password") suspend fun resetPassword(@Body body: ResetPasswordRequest): MessageResponse
    @POST("vpn/clients/register") suspend fun registerVpnClient(@Body body: VpnClientRegisterRequest): VpnClientRegisterResponse
}

interface AccountApi {
    @POST("auth/logout") suspend fun logout()
    @GET("auth/sessions") suspend fun sessions(): List<SessionDto>
    @DELETE("auth/sessions/other") suspend fun revokeOthers(): RevokeOthersResponse
    @DELETE("auth/sessions/{id}") suspend fun revoke(@Path("id") id: String): MessageResponse
    @HTTP(method = "DELETE", path = "auth/account", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): MessageResponse
    @PATCH("auth/password") suspend fun changePassword(@Body body: ChangePasswordRequest)
    @PATCH("auth/profile") suspend fun updateProfile(@Body body: UpdateProfileRequest)
}

interface DeviceApi {
    @GET("devices") suspend fun devices(): JsonElement
    @POST("devices/register") suspend fun register(@Body body: RegisterDeviceRequest)
    @GET("devices/{mac}/status") suspend fun status(@Path("mac") mac: String): DeviceStatusDto
    @PATCH("devices/{mac}") suspend fun update(@Path("mac") mac: String, @Body body: DevicePatchRequest)
}

interface PrivacyZoneApi {
    @GET("devices/{mac}/privacy-zones") suspend fun list(@Path("mac") mac: String): List<PrivacyZoneDto>
    /** 일괄 저장. 보낸 목록이 보드의 전체 존이 되고, 응답은 저장 후 전체 목록 (ICD 2.6) */
    @PUT("devices/{mac}/privacy-zones")
    suspend fun replace(@Path("mac") mac: String, @Body body: PrivacyZonesRequest): List<PrivacyZoneDto>
}

interface EventApi {
    @GET("events") suspend fun events(@QueryMap query: Map<String, String>): EventListDto
    @PATCH("events/{id}") suspend fun update(@Path("id") id: String, @Body body: EventPatchRequest)
}

interface VpnApi {
    @GET("status") suspend fun clientStatus(): ClientVpnStatusDto
    @GET("vpn/connections") suspend fun connections(): JsonElement
    @POST("vpn/connections/{mac}/recover") suspend fun recover(@Path("mac") mac: String)
    @POST("vpn/connections/{mac}/action") suspend fun action(@Path("mac") mac: String, @Body body: VpnActionRequest)
}

interface SettingsApi {
    @GET("general-settings") suspend fun general(): GeneralSettingsDto
    @PATCH("general-settings/{key}") suspend fun updateGeneral(@Path("key") key: String, @Body body: JsonObject)
    @GET("notifications/settings") suspend fun notifications(): NotificationSettingsDto
    @PUT("notifications/settings") suspend fun updateNotifications(@Body body: NotificationSettingsDto)
    @GET("storage/settings") suspend fun storage(): StorageSettingsDto
    @PUT("storage/settings") suspend fun updateStorage(@Body body: StorageSettingsRequest)
    @HTTP(method = "DELETE", path = "storage/files", hasBody = true)
    suspend fun deleteStorageFiles(@Body body: StorageFileDeleteRequest)
}

interface SubscriptionApi {
    @GET("subscriptions") suspend fun subscription(): SubscriptionDto
    @PATCH("subscriptions/plan") suspend fun changePlan(@Body body: PlanChangeRequest): SubscriptionDto
    @PATCH("subscriptions/auto-renew") suspend fun autoRenew(@Body body: AutoRenewRequest)
    @GET("payment-methods") suspend fun paymentMethods(): List<PaymentMethodDto>
    @PATCH("payment-methods/{id}/default") suspend fun setDefault(@Path("id") id: String)
    @DELETE("payment-methods/{id}") suspend fun delete(@Path("id") id: String)
}
