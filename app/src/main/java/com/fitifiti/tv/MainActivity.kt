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

    // Yön tuşu basılı tutulunca TV saniyede ~30 tekrar gönderir; zayıf işlemcide bunlar birikip odak gecikmeli
    // ve "kayarak" ilerliyordu. Tekrarlar en fazla ~11/sn'ye indirilir (ilk basış hiç beklemez).
    private var lastRepeatAt = 0L
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) BackProbe.key(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.keyCode in DPAD) {
            val now = SystemClock.uptimeMillis()
            if (now - lastRepeatAt < 90) return true
            lastRepeatAt = now
        }
        return super.dispatchKeyEvent(event)
    }
    private val DPAD = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

    @Deprecated("ComponentActivity hâlâ çağırıyor; yalnız ölçüm için")
    override fun onBackPressed() { BackProbe.handled(); @Suppress("DEPRECATION") super.onBackPressed() }

    override fun onStart() {
        super.onStart()
        App.instance.remote.start()
        // izin ayarından dönüldüyse kuruluma devam, değilse (en fazla 30 dk'da bir) yeni sürüm var mı bak
        val st = com.fitifiti.tv.data.update.Updater.state.value
        if (st is com.fitifiti.tv.data.update.UpdateState.NeedsPermission && (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()))
            com.fitifiti.tv.data.update.Updater.install(this, st.info)
        else com.fitifiti.tv.data.update.Updater.check()
    }
    override fun onStop() { super.onStop(); App.instance.remote.stop() }
}
