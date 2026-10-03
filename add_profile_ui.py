import os

base_dir = r"c:\Users\planlama\Documents\antigravity\excited-archimedes\fitifiti-tv\app\src\main\java\com\fitifiti\tv"

# --- AddProfileScreen.kt ---
with open(f"{base_dir}/ui/screens/AddProfileScreen.kt", "w", encoding="utf-8") as f:
    f.write("""
package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedTextField
import androidx.tv.material3.Text
import com.fitifiti.tv.viewmodels.ProfilesViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddProfileScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    onProfileSaved: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Yeni Profil Oluştur",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Profil Adı") },
            modifier = Modifier.fillMaxWidth(0.4f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (name.isNotBlank()) {
                    viewModel.addProfile(name = name, avatarUrl = "", pin = null)
                    onProfileSaved()
                }
            }
        ) {
            Text("Kaydet ve Seç", modifier = Modifier.padding(8.dp))
        }
    }
}
""")

# --- Update MainActivity.kt ---
main_path = f"{base_dir}/MainActivity.kt"
with open(main_path, "r", encoding="utf-8") as f:
    content = f.read()

if "AddProfileScreen" not in content:
    content = content.replace(
        "import com.fitifiti.tv.ui.screens.ProfilesScreen", 
        "import com.fitifiti.tv.ui.screens.ProfilesScreen\nimport com.fitifiti.tv.ui.screens.AddProfileScreen"
    )
    
    content = content.replace(
        "onAddProfile = { /* Go to Add Profile */ }",
        "onAddProfile = { navController.navigate(\"add_profile\") }"
    )
    
    content = content.replace(
        "composable(\"home\") {",
        """composable("add_profile") {
                            AddProfileScreen(
                                onProfileSaved = { navController.navigate("profiles") { popUpTo("profiles") { inclusive = true } } }
                            )
                        }
                        composable("home") {"""
    )

    with open(main_path, "w", encoding="utf-8") as f:
        f.write(content)

print("AddProfileScreen added.")
