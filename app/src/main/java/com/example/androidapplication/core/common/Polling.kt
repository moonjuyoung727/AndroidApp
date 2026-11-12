package com.example.androidapplication.core.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull

const val CAMERA_POLL_MS = 5_000L
const val DASHBOARD_POLL_MS = 10_000L
/**
 * 요청 타임아웃 (웹과 같다). 지나면 서버에 닿지 못한 것으로 보고 VPN 끊김으로 바꾼다.
 * 목록(보드·이벤트), 조회(설정·영역·/status)는 5초, VPN 관리 API는 10초. 쓰기 요청에는 걸지 않는다.
 */
const val LIST_TIMEOUT_MS = 5_000L
const val READ_TIMEOUT_MS = 5_000L
const val VPN_API_TIMEOUT_MS = 10_000L
const val EVENT_POLL_MS = 30_000L

/** 조회가 실패하면 성공 주기와 상관없이 이 간격 뒤에 다시 보낸다 (웹과 같다). */
const val POLL_RETRY_MS = 15_000L

/** 자동 반복 조회를 허용하는지 (VPN 끊김이면 false). 로그인 셸이 제공한다. */
val LocalPollGate = staticCompositionLocalOf<Flow<Boolean>> { flowOf(true) }

/**
 * 앞 요청이 끝난 뒤 다음 대기를 시작해 요청이 겹치지 않게 한다.
 * [tick]의 인자는 첫 조회 여부. 자동 재조회(false)는 실패해도 Snackbar를 띄우지 않는다.
 * [tick]은 성공하면 true를 돌려준다. 성공하면 [intervalMs], 실패하면 [retryMs] 뒤에 다시 보낸다.
 * [repeat]이 꺼져 있어도 실패하면 성공할 때까지 [retryMs]마다 다시 보낸다.
 * 실패 재시도를 기다리는 중에 VPN이 복구되면 [repeat]과 상관없이 남은 대기 없이 바로 조회한다.
 * [repeat]이 꺼져 있으면 성공한 뒤에는 VPN이 끊겼다가 복구될 때만 한 번 다시 조회한다.
 * 첫 조회는 항상 보낸다. [online]이 false인 동안 다음 조회는 멈추고,
 * true로 돌아오면 남은 대기 없이 바로 조회한다.
 */
suspend fun pollLoop(
    intervalMs: Long,
    repeat: Boolean,
    online: Flow<Boolean> = flowOf(true),
    retryMs: Long = POLL_RETRY_MS,
    tick: suspend (first: Boolean) -> Boolean
) {
    var ok = tick(true)
    while (true) {
        if (ok && !repeat) {
            // 끊김을 기다린다. 이미 끊겨 있으면 바로 넘어가고, 끊김이 올 수 없는 흐름이면 끝낸다
            online.firstOrNull { !it } ?: return
        } else if (ok) {
            delay(intervalMs)
        } else {
            // 실패 재시도 대기 중에 VPN이 복구되면 남은 대기 없이 바로 조회한다 (웹과 같다).
            // 지금 끊겨 있으면 다음 연결을, 연결돼 있으면 끊겼다가 다시 연결되는 것을 기다린다.
            // 복구가 올 수 없는 흐름(끝나는 흐름)이면 남은 시간을 그대로 기다린다.
            withTimeoutOrNull(retryMs) { online.dropWhile { it }.firstOrNull { it } ?: awaitCancellation() }
        }
        online.first { it }
        ok = tick(false)
    }
}

/**
 * 화면이 STARTED일 때만 조회한다. [repeat]이 꺼져 있으면 진입 시 한 번만 조회하고,
 * 그 뒤로는 실패했을 때와 VPN이 복구됐을 때만 다시 조회한다.
 */
@Composable
fun PollWhileStarted(
    intervalMs: Long,
    repeat: Boolean = true,
    online: Flow<Boolean> = LocalPollGate.current,
    tick: suspend (first: Boolean) -> Boolean
) {
    val owner = LocalLifecycleOwner.current
    val currentTick by rememberUpdatedState(tick)
    LaunchedEffect(owner, intervalMs, repeat, online) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            pollLoop(intervalMs, repeat, online) { first -> currentTick(first) }
        }
    }
}
