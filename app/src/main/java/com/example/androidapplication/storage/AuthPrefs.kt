package com.example.androidapplication.storage

import android.content.Context
import androidx.core.content.edit

/**
 * 로그인 상태 유지(remember me) 저장소.
 * 체크한 채로 로그인하면 아이디·토큰을 남겨 두고, 다음 실행 때 자동 로그인한다.
 */
class AuthPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val rememberMe: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ME, false)

    val savedUserId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)

    // 로그인 상태 유지가 켜져 있고 토큰이 남아 있으면 로그인 화면을 건너뛴다
    val hasSavedSession: Boolean
        get() = rememberMe && !accessToken.isNullOrBlank()

    fun saveSession(userId: String, token: String) {
        prefs.edit {
            putBoolean(KEY_REMEMBER_ME, true)
            putString(KEY_USER_ID, userId)
            putString(KEY_TOKEN, token)
        }
    }

    // 로그아웃: 토큰만 지우고 아이디·체크 상태는 남겨 로그인 화면에 채워 준다
    fun clearSession() {
        prefs.edit { remove(KEY_TOKEN) }
    }

    // 체크를 해제하고 로그인하면 저장된 내용을 모두 지운다
    fun clearAll() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS_NAME = "auth_prefs"
        const val KEY_REMEMBER_ME = "remember_me"
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "access_token"
    }
}
