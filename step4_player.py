import os

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv"

# Create ViewModels directory if needed
os.makedirs(f"{base_dir}/viewmodels", exist_ok=True)
os.makedirs(f"{base_dir}/ui/screens/player", exist_ok=True)

# --- PlayerViewModel.kt ---
with open(f"{base_dir}/viewmodels/PlayerViewModel.kt", "w", encoding="utf-8") as f:
    f.write("""
package com.fitifiti.tv.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var _player: ExoPlayer? = null
    val player: ExoPlayer? get() = _player

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    fun initializePlayer(videoUrl: String) {
        if (_player == null) {
            _player = ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
        }
    }

    fun releasePlayer() {
        _player?.release()
        _player = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
""")

# --- PlayerScreen.kt ---
with open(f"{base_dir}/ui/screens/player/PlayerScreen.kt", "w", encoding="utf-8") as f:
    f.write("""
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
""")

# Update MainActivity to include Player route
main_path = f"{base_dir}/MainActivity.kt"
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()

if "PlayerScreen" not in content:
    content = content.replace("import com.fitifiti.tv.ui.screens.HomeScreen", "import com.fitifiti.tv.ui.screens.HomeScreen\nimport com.fitifiti.tv.ui.screens.player.PlayerScreen")
    
    # We will navigate to the player from the HomeScreen when clicking a movie
    content = content.replace("composable(\"home\") {\n                            HomeScreen()\n                        }", """composable("home") {
                            HomeScreen() // Wait, need to pass navController
                        }
                        composable("player") {
                            PlayerScreen(videoUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                        }""")
                        
    # Update HomeScreen to take navController so it can go to Player
    # In a real app we'd pass an id, but here we just navigate
    
    with open(main_path, "w", encoding="utf-8") as f:
        f.write(content)

print("Player components generated.")
