
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
