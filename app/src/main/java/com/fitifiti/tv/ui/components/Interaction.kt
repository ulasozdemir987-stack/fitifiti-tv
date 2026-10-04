package com.fitifiti.tv.ui.components

import android.os.SystemClock
import android.view.KeyEvent as AKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** OK'in basılı kalma süresi, etkinlik seviyesinde (olaylar Compose'a ulaşmadan) ölçülür */
object OkClock {
    @Volatile private var downAt = 0L
    @Volatile var lastHeld = 0L; private set
    fun record(e: android.view.KeyEvent) {
        val now = SystemClock.uptimeMillis()
        if (e.action == android.view.KeyEvent.ACTION_DOWN && e.repeatCount == 0) downAt = now
        if (e.action == android.view.KeyEvent.ACTION_UP) lastHeld = if (downAt > 0) now - downAt else 0
    }
}

/**
 * OK'e kısa basış = tıklama, basılı tutma (≥ 500 ms) = uzun basış (seçenekler). Kumandaların çoğu Android'in
 * "uzun basış" işaretini göndermiyor (gerçek kutuda görüldü: tv-material Surface'in onLongClick'i hiç tetiklenmiyor,
 * bırakınca normal tıklama oluyordu) → süre burada ölçülür. Pencere, tuş BIRAKILINCA açılır: basılıyken açılsaydı
 * bırakılış penceredeki ilk seçeneğe tıklama olarak düşebilirdi. MENU tuşu da seçenekleri açar.
 */
fun Modifier.okClicks(onClick: () -> Unit, onLong: () -> Unit): Modifier = composed {
    var downAt by remember { mutableLongStateOf(0L) }
    var repeated by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        when (e.key.nativeKeyCode) {
            AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER -> when (e.type) {
                KeyEventType.KeyDown -> {
                    if (e.nativeKeyEvent.repeatCount == 0) { downAt = SystemClock.uptimeMillis(); repeated = false } else repeated = true
                    android.util.Log.i("fitiok", "card down rep=${e.nativeKeyEvent.repeatCount}")
                    true
                }
                KeyEventType.KeyUp -> {
                    // basılış bu öğede başlamadıysa (ör. önceki ekrandan kalan bırakış) yok say
                    if (downAt == 0L) return@onPreviewKeyEvent true
                    val n = e.nativeKeyEvent
                    val long = repeated || n.eventTime - n.downTime >= 500 || SystemClock.uptimeMillis() - downAt >= 500 || OkClock.lastHeld >= 500
                    android.util.Log.i("fitiok", "card up long=$long rep=$repeated held=${OkClock.lastHeld} dt=${SystemClock.uptimeMillis() - downAt}")
                    downAt = 0L
                    if (long) onLong() else onClick()
                    true
                }
                else -> false
            }
            AKey.KEYCODE_MENU -> { if (e.type == KeyEventType.KeyUp) onLong(); true }
            else -> false
        }
    }
}
