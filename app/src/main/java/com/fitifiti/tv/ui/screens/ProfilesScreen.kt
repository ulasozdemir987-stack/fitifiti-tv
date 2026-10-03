package com.fitifiti.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import android.view.KeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.fitifiti.tv.data.local.entity.Profile
import com.fitifiti.tv.viewmodels.ProfilesViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
    onProfileSelected: (Profile) -> Unit,
    onAddProfile: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Kim İzliyor?",
            style = MaterialTheme.typography.displayMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(48.dp))

        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            items(profiles) { profile ->
                ProfileCard(
                    name = profile.name,
                    isAdd = false,
                    onClick = { onProfileSelected(profile) }
                )
            }
            item {
                ProfileCard(
                    name = "Profil Ekle",
                    isAdd = true,
                    onClick = onAddProfile
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfileCard(name: String, isAdd: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(durationMillis = 200), label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(140.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isAdd) Color(0xFF1A1A26) else Color(0xFF12121C))
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                       (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || 
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        onClick()
                        true
                    } else false
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .focusable(interactionSource = interactionSource),
            contentAlignment = Alignment.Center
        ) {
            if (isAdd) {
                Icon(Icons.Default.Add, contentDescription = "Ekle", tint = Color.White, modifier = Modifier.size(48.dp))
            } else {
                // Placeholder for avatar (Coil will be used here later)
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF8B5CF6)))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        androidx.tv.material3.Text(
            text = name,
            style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
            color = if (isFocused) Color.White else Color.Gray
        )
    }
}
