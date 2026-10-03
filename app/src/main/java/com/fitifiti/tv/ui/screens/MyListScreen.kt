
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
