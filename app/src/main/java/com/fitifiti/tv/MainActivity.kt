package com.fitifiti.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.fitifiti.tv.data.remote.RemoteBus
import com.fitifiti.tv.ui.AppRoot
import com.fitifiti.tv.ui.theme.FitifitiTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.fitifiti.tv.data.diag.FrameStats.attach(this)
        // pencere zemini her karede tam ekran boyanıyordu; zemini zaten Compose çiziyor (AppRoot) → bir boyama eksik
        window.setBackgroundDrawable(null)
        setContent {
            FitifitiTheme {
                // Soğuk açılışta kedili logo animasyonu (sitedeki açılış gibi), sonra uygulama
                var splash by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(savedInstanceState == null) }
                if (splash) com.fitifiti.tv.ui.components.BrandSplash { splash = false } else AppRoot()
            }
        }
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
    private var backDownAt = 0L
    private var backLong = false
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Geri tuşu Compose'a HİÇ verilmez: Compose TV'de geri tuşunu önce "odağı üst gruba taşı" (FocusDirection.Exit)
        // olarak kullanıp tüketiyordu → iç içe her odak grubu için bir basış gerekiyordu ("3-4 kez basmam gerekiyor").
        // Doğrudan geri dağıtıcısına (BackHandler'lar) gider. Açılır pencereler kendi penceresinde, etkilenmez.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            BackProbe.key(event)
            // Geri tuşu anında ve takılmadan çalışmalı: gereksiz UI thread freeze ve ekran görüntüsü kaldırıldı
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                BackProbe.handled()
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            com.fitifiti.tv.ui.components.OkClock.record(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.keyCode in DPAD) {
            val now = SystemClock.uptimeMillis()
            if (now - lastRepeatAt < 90) return true
            lastRepeatAt = now
        }
        val handled = super.dispatchKeyEvent(event)
        // hiçbir öğe işlemediyse odak kaybolmuştur → geri ver (yoksa tuşlar boşa gider, uygulama donmuş görünür)
        if (!handled && event.action == KeyEvent.ACTION_DOWN && (event.keyCode in DPAD || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
            val v = window.decorView.findFocus()
            val now = SystemClock.uptimeMillis()
            if (now - lastUnhandledAt >= 3000) { unhandledStreak = 0; unhandledKeys.clear() }
            unhandledStreak++; unhandledKeys += event.keyCode
            lastUnhandledAt = now
            com.fitifiti.tv.data.diag.Diag.log("tuş işlenmedi (${KeyEvent.keyCodeToString(event.keyCode)}) · odak: ${v?.javaClass?.simpleName ?: "yok"} · seri $unhandledStreak")
            // Listenin kenarında aynı yöne basmak normaldir; iki farklı yön de boşa gidiyorsa (ya da 4 kez) odak takılmıştır
            com.fitifiti.tv.ui.FocusRescue.requests.tryEmit(unhandledKeys.size >= 2 || unhandledStreak >= 4)
        } else if (handled && event.action == KeyEvent.ACTION_DOWN) { unhandledStreak = 0; unhandledKeys.clear() }
        return handled
    }
    private var unhandledStreak = 0
    private val unhandledKeys = HashSet<Int>()
    private var lastUnhandledAt = 0L
    private val DPAD = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)

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
