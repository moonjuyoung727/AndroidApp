package com.example.androidapplication.navigation

import kotlinx.serialization.Serializable

@Serializable data object VpnCheckRoute
@Serializable data object DashboardRoute
@Serializable data class MultiviewRoute(val status: String? = null)
@Serializable data class EventsRoute(val status: String? = null, val period: String? = null)
@Serializable data object MediaRoute
@Serializable data object MoreRoute

@Serializable data class MonitoringRoute(val mac: String)
@Serializable data class EventDetailRoute(val eventJson: String)
@Serializable data class MediaDetailRoute(val id: Long)
@Serializable data object DevicesRoute
@Serializable data object VpnManageRoute
@Serializable data object SettingsRoute
@Serializable data object GeneralSettingsRoute
@Serializable data object NotificationSettingsRoute
@Serializable data object SubscriptionRoute
@Serializable data object CloudStorageRoute
@Serializable data object LocalStorageRoute
@Serializable data class PrivacyZoneRoute(val mac: String? = null)
@Serializable data class CameraSettingsRoute(val mac: String? = null)
@Serializable data object AccountRoute
@Serializable data object LoginSessionsRoute
