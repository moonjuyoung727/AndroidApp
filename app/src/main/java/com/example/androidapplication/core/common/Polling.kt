package com.example.androidapplication.core.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

const val CAMERA_POLL_MS = 5_000L
const val DASHBOARD_POLL_MS = 10_000L
const val LIST_TIMEOUT_MS = 5_000L

/**
 * 앞 요청이 끝난 뒤 다음 대기를 시작해 요청이 겹치지 않게 한다.
 * [tick]의 인자는 첫 조회 여부. 자동 재조회(false)는 실패해도 Snackbar를 띄우지 않는다.
 */
suspend fun pollLoop(intervalMs: Long, repeat: Boolean, tick: suspend (first: Boolean) -> Unit) {
    tick(true)
    while (repeat) {
        delay(intervalMs)
        tick(false)
    }
}

/** 화면이 STARTED일 때만 조회한다. [repeat]이 꺼져 있으면 진입 시 한 번만 조회한다. */
@Composable
fun PollWhileStarted(intervalMs: Long, repeat: Boolean = true, tick: suspend (first: Boolean) -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentTick by rememberUpdatedState(tick)
    LaunchedEffect(owner, intervalMs, repeat) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            pollLoop(intervalMs, repeat) { first -> currentTick(first) }
        }
    }
}
