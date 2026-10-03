package com.fitifiti.tv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Tuşa basıldığında (yalnız KeyDown) Android tuş kodunu verir; true dönerse olay tüketilir. */
fun Modifier.onPreviewKeyEventCompat(onDown: (Int) -> Boolean): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) false else onDown(e.key.nativeKeyCode)
}
