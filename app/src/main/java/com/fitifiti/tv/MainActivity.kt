package com.fitifiti.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.fitifiti.tv.data.remote.RemoteBus
import com.fitifiti.tv.ui.AppRoot
import com.fitifiti.tv.ui.theme.FitifitiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FitifitiTheme { AppRoot() } }
        // Telefon kumandasından gelen tuşlar, kumandanın kendi tuşlarıyla aynı yoldan işlenir
        lifecycleScope.launch {
            RemoteBus.keys.collect { k ->
                val code = when (k) {
                    "up" -> KeyEvent.KEYCODE_DPAD_UP; "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "left" -> KeyEvent.KEYCODE_DPAD_LEFT; "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "ok" -> KeyEvent.KEYCODE_DPAD_CENTER; "enter" -> KeyEvent.KEYCODE_ENTER
                    "back" -> KeyEvent.KEYCODE_BACK; "playpause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "rew" -> KeyEvent.KEYCODE_MEDIA_REWIND; "ff" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                    else -> return@collect
                }
                val t = SystemClock.uptimeMillis()
                dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                dispatchKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
            }
        }
    }

    override fun onStart() { super.onStart(); App.instance.remote.start() }
    override fun onStop() { super.onStop(); App.instance.remote.stop() }
}
