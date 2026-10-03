import os

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv"

# --- SearchScreen.kt ---
write_file = f"{base_dir}/ui/screens/SearchScreen.kt"
with open(write_file, "w", encoding="utf-8") as f:
    f.write("""
package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.OutlinedTextField

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(56.dp)
    ) {
        Text("Arama", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Film veya dizi ara...") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (query.isNotEmpty()) {
            Text("'$query' için sonuçlar (Yakında)", color = Color.Gray)
        } else {
            Text("Aramak istediğiniz içeriği yazın.", color = Color.Gray)
        }
    }
}
""")

# --- MyListScreen.kt ---
write_file = f"{base_dir}/ui/screens/MyListScreen.kt"
with open(write_file, "w", encoding="utf-8") as f:
    f.write("""
package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyListScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(56.dp)
    ) {
        Text("Listem", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Listeniz şu an boş. Hoşunuza giden içerikleri daha sonra izlemek için listenize ekleyebilirsiniz.", color = Color.Gray)
    }
}
""")

# --- Update MainActivity.kt ---
main_path = f"{base_dir}/MainActivity.kt"
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()

if "SearchScreen" not in content:
    content = content.replace("import com.fitifiti.tv.ui.screens.live.LiveTvScreen", "import com.fitifiti.tv.ui.screens.live.LiveTvScreen\nimport com.fitifiti.tv.ui.screens.SearchScreen\nimport com.fitifiti.tv.ui.screens.MyListScreen")
    
    content = content.replace(
        "composable(\"live\") {",
        """composable("search") { SearchScreen() }
                        composable("mylist") { MyListScreen() }
                        composable("live") {"""
    )
    with open(main_path, "w", encoding="utf-8") as f:
        f.write(content)

# --- Update HomeScreen.kt with buttons ---
home_path = f"{base_dir}/ui/screens/HomeScreen.kt"
with open(home_path, "r", encoding="utf-8") as f:
    home_content = f.read()

if "onSearchClicked" not in home_content:
    home_content = home_content.replace(
        "fun HomeScreen(onMovieClicked: () -> Unit, onLiveTvClicked: () -> Unit = {}) {",
        "fun HomeScreen(onMovieClicked: () -> Unit, onLiveTvClicked: () -> Unit = {}, onSearchClicked: () -> Unit = {}, onMyListClicked: () -> Unit = {}) {"
    )
    
    # Add buttons next to Canlı TV
    home_content = home_content.replace(
        "androidx.tv.material3.Button(onClick = onLiveTvClicked) { Text(\"Canlı TV'ye Git\") }",
        """Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.tv.material3.Button(onClick = onSearchClicked) { Text("Arama") }
                androidx.tv.material3.Button(onClick = onLiveTvClicked) { Text("Canlı TV") }
                androidx.tv.material3.Button(onClick = onMyListClicked) { Text("Listem") }
            }"""
    )
    
    with open(home_path, "w", encoding="utf-8") as f:
        f.write(home_content)

# Update MainActivity again for HomeScreen params
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace(
    "onLiveTvClicked = { navController.navigate(\"live\") }",
    "onLiveTvClicked = { navController.navigate(\"live\") },\n                                onSearchClicked = { navController.navigate(\"search\") },\n                                onMyListClicked = { navController.navigate(\"mylist\") }"
)
with open(main_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Step 6 Created")
