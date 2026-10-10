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
import com.fitifiti.tv.ui.theme.C

/**
 * "Liquid glass" — sitenin paletinde (mor → turkuaz), gerçek arka plan bulanıklaştırması OLMADAN (zayıf TV ekran
 * kartında bulanıklaştırma her karede pahalı). Cam hissi ışık ve renkle verilir:
 *  - çok hafif koyu ton (arkadaki görsel görünür kalır) + üstten alta azalan beyaz dolgu,
 *  - çapraz mor → turkuaz renk yıkaması (camın içinden geçen marka rengi),
 *  - üst yarıda parlama, alt kenarda turkuaz yansıma,
 *  - kenar ışığı: sol üstte beyaz, sağa doğru mor, sağ altta turkuaz.
 * Çocukları kırpmaz (odaktaki büyüme / parıltı kesilmez); yalnız arkasına çizer.
 */
fun Modifier.glass(shape: Shape, strength: Float = 1f, tint: Color = Color(0x140B0B12)): Modifier = this.drawWithCache {
    val outline: Outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f * strength), Color.White.copy(alpha = 0.01f * strength)))
    val wash = Brush.linearGradient(listOf(C.primary.copy(alpha = 0.09f * strength), Color.Transparent, C.teal.copy(alpha = 0.10f * strength)),
        start = Offset.Zero, end = Offset(size.width, size.height))
    val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f * strength), Color.Transparent), endY = size.height * 0.5f)
    val glow = Brush.verticalGradient(listOf(Color.Transparent, C.teal.copy(alpha = 0.06f * strength)), startY = size.height * 0.68f, endY = size.height)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f * strength), 0.3f to Color.White.copy(alpha = 0.16f * strength),
        0.62f to C.primary.copy(alpha = 0.38f * strength), 1f to C.teal.copy(alpha = 0.55f * strength),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    val stroke = Stroke(1.dp.toPx())
    onDrawBehind {
        drawOutline(outline, tint)
        drawOutline(outline, fill)
        drawOutline(outline, wash)
        clipPath(path) {
            drawRect(sheen, size = Size(size.width, size.height * 0.5f))
            drawRect(glow, topLeft = Offset(0f, size.height * 0.68f), size = Size(size.width, size.height * 0.32f))
        }
        drawOutline(outline, rim, style = stroke)
    }
}
