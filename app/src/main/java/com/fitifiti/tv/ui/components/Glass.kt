package com.fitifiti.tv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * "Liquid glass" görünümü — gerçek arka plan bulanıklaştırması OLMADAN (zayıf TV ekran kartında bulanıklaştırma her
 * karede pahalı; ana sayfa kaydırmasını takıltıyordu). Cam hissi ışıkla verilir:
 *  - koyu yarı saydam renk + üstten alta azalan beyaz dolgu (camın kalınlığı),
 *  - üst yarıda yumuşak parlama (kavisli yüzeye düşen ışık), alt kenarda hafif yansıma,
 *  - sol üstte parlak, sağ altta hafif dönen kenar çizgisi (ışığı kıran kenar).
 * Çocukları kırpmaz (odaktaki büyüme / parıltı kesilmesin); yalnız arkasına çizer.
 */
fun Modifier.glass(shape: Shape, strength: Float = 1f, tint: Color = Color(0x660B0B12)): Modifier = this.drawWithCache {
    val outline: Outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f * strength), Color.White.copy(alpha = 0.06f * strength)))
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.17f * strength), Color.Transparent), endY = size.height * 0.55f)
    val glow = Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f * strength)), startY = size.height * 0.7f, endY = size.height)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.72f * strength), 0.35f to Color.White.copy(alpha = 0.12f * strength),
        0.7f to Color.White.copy(alpha = 0.05f * strength), 1f to Color.White.copy(alpha = 0.32f * strength),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    val stroke = Stroke(1.dp.toPx())
    onDrawBehind {
        drawOutline(outline, tint)
        drawOutline(outline, fill)
        clipPath(path) {
            drawRect(sheen, size = Size(size.width, size.height * 0.55f))
            drawRect(glow, topLeft = Offset(0f, size.height * 0.7f), size = Size(size.width, size.height * 0.3f)) // alttan yansıyan ışık
        }
        drawOutline(outline, rim, style = stroke)
    }
}
