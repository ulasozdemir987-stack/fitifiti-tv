import os

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv"

with open(f"{base_dir}/ui/screens/HomeScreen.kt", "w", encoding="utf-8") as f:
    f.write("""
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
fun HomeScreen() {
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
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryRow(title: String, onItemFocused: (String, String, Color) -> Unit) {
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
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MovieCard(title: String, onFocused: () -> Unit) {
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
            .focusable()
            .clickable { /* Go to Detail */ },
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
""")

# Update MainActivity navigation to include Home
main_path = f"{base_dir}/MainActivity.kt"
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("import com.fitifiti.tv.ui.screens.ProfilesScreen", "import com.fitifiti.tv.ui.screens.ProfilesScreen\nimport com.fitifiti.tv.ui.screens.HomeScreen")
content = content.replace("onProfileSelected = { /* Go to Home */ }", "onProfileSelected = { navController.navigate(\"home\") }")
content = content.replace("composable(\"profiles\") {", """composable("profiles") {
                            ProfilesScreen(
                                onProfileSelected = { navController.navigate("home") },
                                onAddProfile = { /* Go to Add Profile */ }
                            )
                        }
                        composable("home") {""")
content = content.replace("composable(\"home\") {", "composable(\"home\") {\n                            HomeScreen()\n                        }")

with open(main_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Home Screen generated.")
