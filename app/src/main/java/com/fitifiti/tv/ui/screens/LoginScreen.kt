package com.fitifiti.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitifiti.tv.viewmodels.LoginViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel(), onLoginSuccess: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Fıtıfıtı Stream", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.server,
            onValueChange = { viewModel.updateServer(it) },
            label = { Text("Sunucu Adresi (örn: http://tv.example.com:8080)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth(0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = state.username,
            onValueChange = { viewModel.updateUsername(it) },
            label = { Text("Kullanıcı Adı") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth(0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = state.password,
            onValueChange = { viewModel.updatePassword(it) },
            label = { Text("Şifre") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth(0.5f)
        )
        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            Button(onClick = { viewModel.testConnection() }) {
                Text("Bağlantıyı Test Et")
            }
            OutlinedButton(onClick = { onLoginSuccess() }) {
                Text("Test Modu (Atla)")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (state.isLoading) {
            Text("Bağlanıyor...", color = MaterialTheme.colorScheme.primary)
        }
        
        state.error?.let {
            Text("Hata: $it", color = Color.Red)
        }
        
        state.userInfo?.let {
            LaunchedEffect(Unit) { onLoginSuccess() }

            Text("Başarılı! Bitiş: ${it.expDate ?: "Sınırsız"}, Aktif Bağlantı: ${it.activeConnections}", color = Color.Green)
        }
    }
}
