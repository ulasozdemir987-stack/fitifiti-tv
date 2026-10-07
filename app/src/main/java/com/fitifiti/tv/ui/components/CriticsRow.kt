package com.fitifiti.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.fitifiti.tv.data.tmdb.Critics
import com.fitifiti.tv.ui.theme.C

/** JetStream sade rozet satırı: IMDb altın kutu, Rotten Tomatoes yüzde domates, Metacritic temiz skor */
@Composable
fun CriticsRow(c: Critics?, modifier: Modifier = Modifier) {
    if (c == null) return
    val imdb = c.imdb?.takeIf { it > 0 && (c.imdbVotes ?: 0) >= 50 }
    if (imdb == null && c.rt == null && c.mc == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (imdb != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("IMDb", Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFF5C518)).padding(horizontal = 6.dp, vertical = 1.dp),
                color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Text("%.1f".format(imdb), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        c.rt?.let { rt ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (rt >= 60) Color(0xFFFA320A) else Color(0xFF7CB342)))
                Spacer(Modifier.width(6.dp))
                Text("$rt%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        c.mc?.let { mc ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bg = when { mc >= 61 -> Color(0xFF66CC33); mc >= 40 -> Color(0xFFFFCC33); else -> Color(0xFFFF0000) }
                Text("$mc", Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
                    color = if (mc in 40..60) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
