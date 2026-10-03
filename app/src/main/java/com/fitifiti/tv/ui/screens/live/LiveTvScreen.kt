package com.fitifiti.tv.ui.screens.live

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.fitifiti.tv.viewmodels.LiveViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveTvScreen(
    viewModel: LiveViewModel = hiltViewModel(),
    onChannelClicked: (String) -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.channels.collectAsState()
    var selectedCategory by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Sidebar (Categories)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(250.dp)
                .background(Color(0xFF12121C))
                .padding(vertical = 32.dp)
        ) {
            TvLazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(categories) { category ->
                    CategoryItem(
                        title = category,
                        isSelected = selectedCategory == category,
                        onFocused = {
                            selectedCategory = category
                            viewModel.onCategorySelected(category)
                        }
                    )
                }
            }
        }

        // Main Content (Channels List)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Text(
                text = if (selectedCategory.isEmpty()) "Tüm Kanallar" else selectedCategory,
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                items(channels) { channel ->
                    ChannelItem(
                        name = channel,
                        onClicked = { onChannelClicked(channel) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CategoryItem(title: String, isSelected: Boolean, onFocused: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF8B5CF6) else if (isSelected) Color(0xFF1A1A26) else Color.Transparent)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = if (isFocused || isSelected) Color.White else Color.Gray,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelItem(name: String, onClicked: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color(0xFF1A1A26) else Color(0xFF12121C))
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
            .clickable { onClicked() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dummy Channel Logo Placeholder
        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray))
        
        Spacer(modifier = Modifier.width(24.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(text = "Şu anki program: Fıtıfıtı Haberleri", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
        
        if (isFocused) {
            Text("İzle", color = Color(0xFF8B5CF6), style = MaterialTheme.typography.labelLarge)
        }
    }
}
