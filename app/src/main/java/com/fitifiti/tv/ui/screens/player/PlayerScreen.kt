
package com.fitifiti.tv.ui.screens.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.fitifiti.tv.viewmodels.PlayerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isPlaying by viewModel.isPlaying.collectAsState()
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(videoUrl) {
        viewModel.initializePlayer(videoUrl)
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    showControls = true
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            viewModel.player?.let {
                                if (it.isPlaying) it.pause() else it.play()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            viewModel.player?.let { it.seekTo(it.currentPosition + 10000) }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            viewModel.player?.let { it.seekTo(it.currentPosition - 10000) }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video Surface
        DisposableEffect(Unit) {
            onDispose { viewModel.releasePlayer() }
        }

        viewModel.player?.let { exoPlayer ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false // We use our custom Compose overlay
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(32.dp)
            ) {
                // Top Title
                Text(
                    text = "Örnek Video Başlığı",
                    color = Color.White,
                    style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Play/Pause Center
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                        .background(Color(0xFF8B5CF6), shape = androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶",
                        color = Color.White,
                        style = androidx.tv.material3.MaterialTheme.typography.displayMedium
                    )
                }

                // Bottom Progress Bar (Placeholder)
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("00:00", color = Color.White)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .height(4.dp)
                            .background(Color.DarkGray)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(0.3f).height(4.dp).background(Color(0xFF8B5CF6)))
                    }
                    Text("45:00", color = Color.White)
                }
            }
        }
    }
}
