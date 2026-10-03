package com.fitifiti.tv.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitifiti.tv.data.repository.XtreamRepository
import com.fitifiti.tv.data.model.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginState(
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val userInfo: UserInfo? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: XtreamRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun updateServer(value: String) { _state.update { it.copy(server = value) } }
    fun updateUsername(value: String) { _state.update { it.copy(username = value) } }
    fun updatePassword(value: String) { _state.update { it.copy(password = value) } }

    fun testConnection() {
        if (_state.value.server.isBlank() || _state.value.username.isBlank() || _state.value.password.isBlank()) {
            _state.update { it.copy(error = "Lütfen tüm alanları doldurun.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, userInfo = null) }
            try {
                var serverUrl = _state.value.server.trim()
                if (!serverUrl.startsWith("http")) {
                    serverUrl = "http://$serverUrl"
                }
                if (serverUrl.endsWith("/")) {
                    serverUrl = serverUrl.dropLast(1)
                }
                
                val result = repository.authenticate(
                    server = serverUrl,
                    username = _state.value.username.trim(),
                    password = _state.value.password.trim()
                )
                if (result.userInfo?.status == "Active" || result.userInfo?.username != null) {
                    _state.update { it.copy(isLoading = false, userInfo = result.userInfo) }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Kullanıcı bilgileri hatalı veya hesap pasif.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.localizedMessage ?: "Bağlantı hatası") }
            }
        }
    }
}
