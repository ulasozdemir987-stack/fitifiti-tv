import os

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv"

# --- SettingsScreen.kt ---
write_file = f"{base_dir}/ui/screens/SettingsScreen.kt"
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
import androidx.tv.material3.Button

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    var tmdbKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(56.dp)
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("TMDb API Anahtarı (İsteğe bağlı zenginleştirme)", color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = tmdbKey,
            onValueChange = { tmdbKey = it },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(0.5f)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(onClick = onLogout) {
            Text("Hesaptan Çıkış Yap", modifier = Modifier.padding(8.dp))
        }
    }
}
""")

# --- Update MainActivity.kt ---
main_path = f"{base_dir}/MainActivity.kt"
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()

if "SettingsScreen" not in content:
    content = content.replace("import com.fitifiti.tv.ui.screens.MyListScreen", "import com.fitifiti.tv.ui.screens.MyListScreen\nimport com.fitifiti.tv.ui.screens.SettingsScreen")
    
    content = content.replace(
        "composable(\"mylist\") { MyListScreen() }",
        """composable("mylist") { MyListScreen() }
                        composable("settings") { 
                            SettingsScreen(onLogout = { navController.navigate("login") { popUpTo(0) } })
                        }"""
    )
    with open(main_path, "w", encoding="utf-8") as f:
        f.write(content)

# --- Update HomeScreen.kt with Settings button ---
home_path = f"{base_dir}/ui/screens/HomeScreen.kt"
with open(home_path, "r", encoding="utf-8") as f:
    home_content = f.read()

if "onSettingsClicked" not in home_content:
    home_content = home_content.replace(
        "onMyListClicked: () -> Unit = {}) {",
        "onMyListClicked: () -> Unit = {}, onSettingsClicked: () -> Unit = {}) {"
    )
    
    home_content = home_content.replace(
        "androidx.tv.material3.Button(onClick = onMyListClicked) { Text(\"Listem\") }",
        """androidx.tv.material3.Button(onClick = onMyListClicked) { Text("Listem") }
                androidx.tv.material3.Button(onClick = onSettingsClicked) { Text("Ayarlar") }"""
    )
    
    with open(home_path, "w", encoding="utf-8") as f:
        f.write(home_content)

# Update MainActivity again for HomeScreen params
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()
content = content.replace(
    "onMyListClicked = { navController.navigate(\"mylist\") }",
    "onMyListClicked = { navController.navigate(\"mylist\") },\n                                onSettingsClicked = { navController.navigate(\"settings\") }"
)
with open(main_path, "w", encoding="utf-8") as f:
    f.write(content)

print("Step 7 Created")
