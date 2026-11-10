package com.example.androidapplication.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.androidapplication.core.media.LocalStorageSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** 서버 명세에 없고 이 기기에만 남기는 설정. */
@Singleton
class LocalPreferences @Inject constructor(
    @param:Named("settings") private val store: DataStore<Preferences>
) {
    val notifications: Flow<LocalNotificationSettings> = store.data.map {
        LocalNotificationSettings(
            vpn = it[VPN] ?: true,
            pushEnabled = it[PUSH] ?: true,
            sound = it[SOUND] ?: true,
            vibrate = it[VIBRATE] ?: true
        )
    }

    suspend fun saveNotifications(value: LocalNotificationSettings) {
        store.edit {
            it[VPN] = value.vpn
            it[PUSH] = value.pushEnabled
            it[SOUND] = value.sound
            it[VIBRATE] = value.vibrate
        }
    }

    val localStorage: Flow<LocalStorageSettings> = store.data.map {
        LocalStorageSettings(
            retentionDays = it[RETENTION] ?: 0,
            capBytes = it[CAP] ?: LocalStorageSettings.DEFAULT_CAP,
            deleteOldestOverCap = it[DELETE_OLDEST] ?: false
        )
    }

    suspend fun localStorageNow(): LocalStorageSettings = localStorage.first()

    suspend fun saveLocalStorage(value: LocalStorageSettings) {
        store.edit {
            it[RETENTION] = value.retentionDays
            it[CAP] = value.capBytes
            it[DELETE_OLDEST] = value.deleteOldestOverCap
        }
    }

    private companion object {
        val VPN = booleanPreferencesKey("notify_vpn")
        val PUSH = booleanPreferencesKey("notify_push")
        val SOUND = booleanPreferencesKey("notify_sound")
        val VIBRATE = booleanPreferencesKey("notify_vibrate")
        val RETENTION = intPreferencesKey("local_retention_days")
        val CAP = longPreferencesKey("local_cap_bytes")
        val DELETE_OLDEST = booleanPreferencesKey("local_delete_oldest")
    }
}
