
package com.fitifiti.tv.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(onMovieClicked: () -> Unit, onLiveTvClicked: () -> Unit = {}, onSearchClicked: () -> Unit = {}, onMyListClicked: () -> Unit = {}, onSettingsClicked: () -> Unit = {}) {
    var heroTitle by remember { mutableStateOf("Fıtıfıtı Stream'e Hoşgeldiniz") }
    var heroDesc by remember { mutableStateOf("Kumandanızla aşağıya inerek filmleri keşfedin.") }
    var heroColor by remember { mutableStateOf(Color(0xFF8B5CF6)) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Hero Background
        Crossfade(targetState = heroColor, animationSpec = tween(800), label = "heroBg") { color ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
                        )
                    )
            )
        }

        // Hero Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp, top = 56.dp, end = 56.dp)
        ) {
            Text(
                text = heroTitle,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = heroDesc,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.tv.material3.Button(onClick = onSearchClicked) { Text("Arama") }
                androidx.tv.material3.Button(onClick = onLiveTvClicked) { Text("Canlı TV") }
                androidx.tv.material3.Button(onClick = onMyListClicked) { Text("Listem") }
                androidx.tv.material3.Button(onClick = onSettingsClicked) { Text("Ayarlar") }
            }
        }

        // Categories (Rows)
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 300.dp, bottom = 56.dp)
        ) {
            val dummyCategories = listOf("Yeni Eklenenler", "Netflix'te", "Aksiyon", "Komedi", "Korku")
            items(dummyCategories) { category ->
                CategoryRow(
                    title = category,
                    onItemFocused = { title, desc, color ->
                        heroTitle = title
                        heroDesc = desc
                        heroColor = color
                    },
                    onItemClicked = onMovieClicked
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryRow(title: String, onItemFocused: (String, String, Color) -> Unit, onItemClicked: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 56.dp, bottom = 12.dp)
        )
        
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(10) { index ->
                MovieCard(
                    title = "$title Film ${index + 1}",
                    onFocused = {
                        onItemFocused(
                            "$title Film ${index + 1}",
                            "Bu harika yapım $title kategorisinde en çok izlenenler arasında yer alıyor. Yönetmen: Fıtıfıtı.",
                            if (index % 2 == 0) Color(0xFFE50914) else Color(0xFF00C2A8)
                        )
                    },
                    onClicked = onItemClicked
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(title: String, onFocused: () -> Unit, onClicked: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(800) // 0.8s focus delay for Hero update
            onFocused()
        }
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(240.dp)
            .scale(if (isFocused) 1.1f else 1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(Color(0xFF12121C))
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                   (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClicked()
                    true
                } else false
            }
            .focusable()
            .clickable { onClicked() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
