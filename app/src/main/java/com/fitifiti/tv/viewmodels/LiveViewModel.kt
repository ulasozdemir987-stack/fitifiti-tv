package com.fitifiti.tv.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveViewModel @Inject constructor() : ViewModel() {
    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories = _categories.asStateFlow()

    private val _channels = MutableStateFlow<List<String>>(emptyList())
    val channels = _channels.asStateFlow()

    init {
        loadDummyLiveTv()
    }

    private fun loadDummyLiveTv() {
        viewModelScope.launch {
            _categories.value = listOf("Ulusal", "Haber", "Spor", "Belgesel", "Sinema", "Çocuk", "Müzik")
            _channels.value = (1..20).map { "Kanal $it" }
        }
    }
    
    fun onCategorySelected(category: String) {
        _channels.value = (1..15).map { "$category Kanalı $it" }
    }
}
