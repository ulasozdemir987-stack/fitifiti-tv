package com.fitifiti.tv.ui.screens

import com.fitifiti.tv.ui.rememberFocus
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.fitifiti.tv.App
import com.fitifiti.tv.data.tmdb.CastMember
import com.fitifiti.tv.data.xtream.Variant
import com.fitifiti.tv.ui.LocalActions
import com.fitifiti.tv.ui.components.*
import com.fitifiti.tv.ui.theme.C
import com.fitifiti.tv.ui.theme.Display

/** Detay sayfası iskeleti: kenardan kenara sahne görseli + kaydırılabilir içerik */
@Composable
fun DetailScaffold(art: HeroArt, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        HeroBackdrop(art)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), content = content)
    }
}

/** "Sürüm" çipleri (Orijinal dil / Türkçe dublaj / 4K) */
@Composable
fun VariantPicker(variants: List<Variant>, selected: Variant?, onSelect: (Variant) -> Unit) {
    if (variants.size < 2) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sürüm", style = MaterialTheme.typography.labelLarge, color = C.muted)
        Spacer(Modifier.width(4.dp))
        variants.forEach { v -> Chip(v.label, v.id == (selected?.id ?: variants.first().id), { onSelect(v) }) }
    }
}

/** Bilgi satırları: "Yönetmen  …" (çerçevesiz, sitedeki InfoList) */
@Composable
fun InfoList(rows: List<Pair<String, String?>>, modifier: Modifier = Modifier) {
    val list = rows.filter { !it.second.isNullOrBlank() }
    if (list.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        list.forEach { (k, v) ->
            Row {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = C.faint, modifier = Modifier.width(110.dp))
                Text(v!!, style = MaterialTheme.typography.bodyMedium, color = Color(0xCCFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.castRow(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    item(key = "cast") {
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionTitle("Oyuncular")
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(cast.distinctBy { it.name + it.role }, key = { it.name + it.role }) { c -> CastCard(c) }
                }
            }
        }
    }
}

@Composable
private fun CastCard(c: CastMember) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = {}, modifier = Modifier.size(96.dp).rememberFocus(),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = C.fill2, focusedContainerColor = C.fill3),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.5.dp, Color.White), shape = CircleShape)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val initials = c.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }
                Text(initials, style = Display.copy(fontSize = 26.sp, color = Color(0x99FFFFFF)))
                if (c.photo != null) AsyncImage(model = c.photo, contentDescription = c.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(c.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!c.role.isNullOrBlank()) Text(c.role, style = MaterialTheme.typography.labelSmall, color = C.faint, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Afişli şerit (detay sayfalarında, kartın altında ad yazılı) */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.plainPosterRow(key: String, title: String, all: List<Item>) {
    val items = all.distinctBy { it.key }
    if (items.isEmpty()) return
    item(key = key) {
        val actions = LocalActions.current
        Column(Modifier.padding(bottom = 26.dp)) {
            SectionTitle(title)
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberRowSpec()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(items, key = { it.key }) { PosterCard(it, onClick = { open(actions, it) }, width = 140.dp) }
                }
            }
        }
    }
}

/** Benzer içerik: aynı kategori + ortak tür, puanı yüksek ve yeni olan önce */
fun <T> similarOf(all: List<T>, self: T, cat: (T) -> String?, genre: (T) -> String?, rating: (T) -> Double, id: (T) -> Int): List<T> {
    val myGenres = com.fitifiti.tv.domain.Ranking.splitGenres(genre(self)).toSet()
    val myCat = cat(self)
    return all.asSequence().filter { id(it) != id(self) }.map { it to (
        (if (cat(it) == myCat) 2.0 else 0.0) + com.fitifiti.tv.domain.Ranking.splitGenres(genre(it)).count { g -> g in myGenres } * 1.5 +
            rating(it).let { r -> if (r in 0.1..9.2) r / 4 else 0.0 })
    }.filter { it.second >= 2.5 }.sortedByDescending { it.second }.take(20).map { it.first }.toList()
}
