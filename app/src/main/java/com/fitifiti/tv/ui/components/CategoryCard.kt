package com.fitifiti.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.domain.categoryStyle
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display

private val LOGO_FILE = mapOf("exxen" to "exxen.png", "gain" to "gain.png")
fun brandLogo(key: String?): String? = key?.let { "file:///android_asset/brands/${LOGO_FILE[it] ?: "$it.svg"}" }
/** Marvel kendi renginde, TOD/Exxen/Gain zaten beyaz; diğerleri beyaza boyanır (sitedeki brightness(0) invert(1)) */
fun brandTint(key: String?): ColorFilter? = if (key in setOf("marvel", "tod", "exxen", "gain")) null else ColorFilter.tint(Color.White)

/** Keşfet kartı: platform logosu ya da okunur kategori adı; sol alttan sitenin mor → turkuaz geçişinin çok hafif tonu */
@Composable
fun CategoryCard(rawName: String, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 240.dp, posters: List<String> = emptyList()) {
    val st = categoryStyle(rawName)
    Surface(
        onClick = onClick, modifier = modifier.width(width).height(width * 0.5f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = C.panel, focusedContainerColor = C.panel),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, C.line), shape = RoundedCornerShape(14.dp)),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(14.dp)),
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(C.primary.copy(alpha = 0.16f), C.teal.copy(alpha = 0.05f), Color.Transparent), start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY), end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)))) {
            // sağda üst üste iki-üç afiş
            posters.take(3).forEachIndexed { i, p ->
                AsyncImage(model = p, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = (14 + i * 26).dp).width(width * 0.2f).aspectRatio(2f / 3f)
                        .background(C.panel, RoundedCornerShape(6.dp)).then(Modifier), alpha = 0.55f + i * 0.15f)
            }
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (posters.isEmpty()) 1f else 0.58f).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                if (st.logo != null) AsyncImage(model = brandLogo(st.logo), contentDescription = st.label, contentScale = ContentScale.Fit, colorFilter = brandTint(st.logo),
                    modifier = Modifier.fillMaxWidth(0.82f).heightIn(max = 40.dp))
                else Text(st.label, style = Display.copy(fontSize = 19.sp), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
