package com.fitifiti.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.fitifiti.tv.ui.AppRoot
import com.fitifiti.tv.ui.theme.FitifitiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FitifitiTheme { AppRoot() } }
    }
}
