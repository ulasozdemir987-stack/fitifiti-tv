
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
