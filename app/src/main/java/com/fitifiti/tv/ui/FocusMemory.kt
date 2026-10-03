package com.fitifiti.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Her ekran (ScreenLayer) en son odaklanan öğeyi kendisi hatırlar. `focusRestorer` yalnız bir seviye geri
 * yüklüyordu: detaydan geri dönünce odak bazen üst çubuktaki ilk sekmeye düşüyor, sekme "üzerinde durunca açılır"
 * olduğu için kullanıcı Filmler'den Ana Sayfa'ya atılıyordu.
 */
class FocusMemory {
    /** en son odaklanan öğe (ekran pasifleşince de kalır) */
    var last: FocusRequester? = null
    /** şu an odakta olan öğe (yoksa null) */
    var current: FocusRequester? = null
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/** Odaklanabilir öğeye eklenir; odak alınca ekranın "son odak"ı olur */
@Composable
fun Modifier.rememberFocus(): Modifier {
    val mem = LocalFocusMemory.current ?: return this
    val fr = remember { FocusRequester() }
    return this.focusRequester(fr).onFocusChanged { if (it.isFocused) { mem.last = fr; mem.current = fr } else if (mem.current === fr) mem.current = null }
}
