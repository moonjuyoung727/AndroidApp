package com.example.androidapplication.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import com.example.androidapplication.model.VideoPlaybackStatus


@Composable
fun HlsPlayer(
    src: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var status by remember {mutableStateOf(VideoPlaybackStatus.LOADING)}

    var reloadKey by remember { mutableStateOf(0) }

    // ExoPlayer 생성
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    // 영상 URL 또는 reloadKey 변경
    LaunchedEffect(src, reloadKey) {
        if (src.isBlank()) {
            return@LaunchedEffect
        }

        status = VideoPlaybackStatus.LOADING

        val mediaItem = MediaItem.fromUri(src)

        exoPlayer.setMediaItem(mediaItem)

        exoPlayer.prepare()

        exoPlayer.play()
    }

    // Player 상태 감지
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {

                    Player.STATE_BUFFERING -> {
                        status = VideoPlaybackStatus.LOADING
                    }

                    Player.STATE_ENDED -> {
                        status = VideoPlaybackStatus.ENDED
                    }
                }
            }

            override fun onIsPlayingChanged(
                isPlaying: Boolean
            ) {
                if (isPlaying) {
                    status = VideoPlaybackStatus.PLAYING
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = VideoPlaybackStatus.ERROR
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Column(
        modifier = modifier
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            }
        )

        when (status) {
            VideoPlaybackStatus.LOADING -> {
                Text("영상 불러오는 중 ...")
            }

            VideoPlaybackStatus.PLAYING -> {
                Text("● 재생 중")
            }

            VideoPlaybackStatus.RECONNECTING -> {
                Text("재연결 중 ...")
            }

            VideoPlaybackStatus.ERROR -> {
                Button(
                    onClick = {
                        status = VideoPlaybackStatus.RECONNECTING
                        reloadKey++
                    }
                ) { Text("↻ 재연결") }
            }

            VideoPlaybackStatus.ENDED -> {
                Text("재생이 종료되었습니다.")
            }
        }
    }
}