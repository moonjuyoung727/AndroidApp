package com.example.androidapplication.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.androidapplication.core.common.SESSION_EXPIRED_MESSAGE
import com.example.androidapplication.core.network.TokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class Session(val token: String, val userId: String, val name: String, val email: String)

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: Session) : AuthState
}

@Singleton
class SessionManager @Inject constructor(
    @param:Named("session") private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
    @param:Named("app") private val scope: CoroutineScope
) : TokenProvider {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _loginNotice = MutableStateFlow<String?>(null)
    val loginNotice: StateFlow<String?> = _loginNotice.asStateFlow()

    val session: Session? get() = (_state.value as? AuthState.LoggedIn)?.session
    override val currentToken: String? get() = session?.token

    /** 앱 시작 시 저장된 세션을 읽는다. "로그인 상태 유지"가 꺼져 있었다면 토큰이 없다. */
    suspend fun restore() {
        // Activity만 다시 만들어진 경우 프로세스에 살아 있는 세션을 덮어쓰지 않는다
        if (_state.value != AuthState.Loading) return
        val prefs = store.data.first()
        val token = prefs[KEY_TOKEN]?.let(cipher::decrypt)
        _state.value = if (prefs[KEY_REMEMBER] == true && !token.isNullOrEmpty()) {
            AuthState.LoggedIn(Session(token, prefs[KEY_USER_ID].orEmpty(), prefs[KEY_NAME].orEmpty(), prefs[KEY_EMAIL].orEmpty()))
        } else {
            AuthState.LoggedOut
        }
    }

    suspend fun rememberedLogin(): Pair<String, Boolean> {
        val prefs = store.data.first()
        return prefs[KEY_EMAIL].orEmpty() to (prefs[KEY_REMEMBER] == true)
    }

    /** 켜면 토큰과 이메일을 저장하고, 끄면 토큰은 메모리에만 둔다 (저장된 이메일·체크 상태도 지운다). */
    suspend fun login(session: Session, remember: Boolean) {
        store.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            if (remember) {
                prefs[KEY_REMEMBER] = true
                prefs[KEY_EMAIL] = session.email
                prefs[KEY_TOKEN] = cipher.encrypt(session.token)
                prefs[KEY_USER_ID] = session.userId
                prefs[KEY_NAME] = session.name
            } else {
                prefs.remove(KEY_REMEMBER)
                prefs.remove(KEY_EMAIL)
                prefs.remove(KEY_USER_ID)
                prefs.remove(KEY_NAME)
            }
        }
        _loginNotice.value = null
        _state.value = AuthState.LoggedIn(session)
    }

    /** 로그아웃은 토큰만 지우고 이메일과 체크 상태는 남긴다. */
    suspend fun clear() {
        _state.value = AuthState.LoggedOut
        store.edit {
            it.remove(KEY_TOKEN)
            it.remove(KEY_USER_ID)
            it.remove(KEY_NAME)
        }
    }

    /** APP-F-061 로그인 설정: 켜면 현재 토큰을 암호화해 저장하고, 끄면 저장본만 지운다. */
    suspend fun setRemember(remember: Boolean) {
        val current = session ?: return
        store.edit { prefs ->
            if (remember) {
                prefs[KEY_REMEMBER] = true
                prefs[KEY_EMAIL] = current.email
                prefs[KEY_TOKEN] = cipher.encrypt(current.token)
                prefs[KEY_USER_ID] = current.userId
                prefs[KEY_NAME] = current.name
            } else {
                prefs.remove(KEY_REMEMBER)
                prefs.remove(KEY_TOKEN)
                prefs.remove(KEY_USER_ID)
                prefs.remove(KEY_NAME)
            }
        }
    }

    suspend fun updateName(name: String) {
        val current = session ?: return
        _state.value = AuthState.LoggedIn(current.copy(name = name))
        store.edit { if (it.contains(KEY_NAME)) it[KEY_NAME] = name }
    }

    fun consumeLoginNotice() {
        _loginNotice.value = null
    }

    override fun onUnauthorized(token: String, serverMessage: String?) {
        synchronized(this) {
            if (session?.token != token) return
            _state.value = AuthState.LoggedOut
            _loginNotice.value = serverMessage?.takeIf { it.isNotBlank() } ?: SESSION_EXPIRED_MESSAGE
        }
        scope.launch { store.edit { it.remove(KEY_TOKEN); it.remove(KEY_USER_ID); it.remove(KEY_NAME) } }
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("token_enc")
        val KEY_EMAIL = stringPreferencesKey("email")
        val KEY_USER_ID = stringPreferencesKey("user_id")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_REMEMBER = booleanPreferencesKey("remember")
    }
}
