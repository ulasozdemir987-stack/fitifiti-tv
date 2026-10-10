package com.fitifiti.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * En son odaklanan öğeyi hatırlar (ekran katmanı başına bir tane; ana ekranın sekme içeriği için de bir alt hafıza).
 * Compose'un `focusRestorer()`'ı KULLANILMAZ: iç içe kullanınca 1.7'de "Release should only be called once"
 * (LazyLayoutPinnableItem) ile çöküyordu (2.5.0) ve üst çubuktan ↓ çalışmıyordu.
 * `focusRestorer` yalnız bir seviye geri yüklüyordu: detaydan geri dönünce odak bazen üst çubuktaki ilk sekmeye
 * düşüyor, sekme "üzerinde durunca açılır" olduğu için kullanıcı Filmler'den Ana Sayfa'ya atılıyordu.
 */
class FocusMemory(private val parent: FocusMemory? = null) {
    /** en son odaklanan öğe (ekran pasifleşince de kalır; öğe bileşimden çıkınca silinir) */
    var last: FocusRequester? = null
        private set
    /** şu an odakta olan öğe (yoksa null) */
    var current: FocusRequester? = null
        private set

    fun focused(fr: FocusRequester) { last = fr; current = fr; parent?.focused(fr) }
    fun blurred(fr: FocusRequester) { if (current === fr) current = null; parent?.blurred(fr) }
    fun gone(fr: FocusRequester) { if (last === fr) last = null; if (current === fr) current = null; parent?.gone(fr) }

    /** Hatırlanan öğeye odaklan; yoksa ya da artık yoksa false */
    fun restore(): Boolean {
        val t = last ?: return false
        runCatching { t.requestFocus() }
        return current === t
    }
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/** Odaklanabilir öğeye eklenir; odak alınca ekranın (ve varsa alt bölümün) "son odak"ı olur */
@Composable
fun Modifier.rememberFocus(): Modifier {
    val mem = LocalFocusMemory.current ?: return this
    val fr = remember { FocusRequester() }
    // kaydırılıp bileşimden çıkan (ya da silinen) öğeye geri odaklanmaya çalışma
    DisposableEffect(mem, fr) { onDispose { mem.gone(fr) } }
    return this.focusRequester(fr).onFocusChanged { if (it.isFocused) mem.focused(fr) else mem.blurred(fr) }
}

/**
 * Odak kurtarma: kumanda yön/OK tuşu hiçbir öğe tarafından işlenmezse (odak kaybolmuşsa) MainActivity buraya haber
 * verir; etkin ekran katmanı odağı son öğeye, o yoksa sayfanın ilk öğesine geri verir. Eskiden odak kaybolunca
 * (ör. fragmanlı detaydan geri dönüş) tuşların hepsi boşa gidiyor, uygulama "donmuş" sanılıyordu.
 */
object FocusRescue {
    val requests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
