package com.fitifiti.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.fitifiti.tv.ui.theme.FitifitiTheme
import com.fitifiti.tv.ui.screens.LoginScreen
import com.fitifiti.tv.ui.screens.ProfilesScreen
import com.fitifiti.tv.ui.screens.AddProfileScreen
import com.fitifiti.tv.ui.screens.HomeScreen
import com.fitifiti.tv.ui.screens.player.PlayerScreen
import com.fitifiti.tv.ui.screens.live.LiveTvScreen
import com.fitifiti.tv.ui.screens.SearchScreen
import com.fitifiti.tv.ui.screens.MyListScreen
import com.fitifiti.tv.ui.screens.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitifitiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "login") {
                        composable("login") {
                            LoginScreen(
                                onLoginSuccess = { navController.navigate("profiles") { popUpTo("login") { inclusive = true } } }
                            )
                        }
                        composable("profiles") {
                            ProfilesScreen(
                                onProfileSelected = { navController.navigate("home") },
                                onAddProfile = { navController.navigate("add_profile") }
                            )
                        }
                        composable("add_profile") {
                            AddProfileScreen(
                                onProfileSaved = { navController.navigate("profiles") { popUpTo("profiles") { inclusive = true } } }
                            )
                        }
                        composable("home") {
                            HomeScreen(
                                onMovieClicked = { navController.navigate("player") },
                                onLiveTvClicked = { navController.navigate("live") },
                                onSearchClicked = { navController.navigate("search") },
                                onMyListClicked = { navController.navigate("mylist") },
                                onSettingsClicked = { navController.navigate("settings") }
                            )
                        }
                        composable("search") { SearchScreen() }
                        composable("mylist") { MyListScreen() }
                        composable("settings") { 
                            SettingsScreen(onLogout = { navController.navigate("login") { popUpTo(0) } })
                        }
                        composable("live") {
                            LiveTvScreen(
                                onChannelClicked = { navController.navigate("player") }
                            )
                        }
                        composable("player") {
                            PlayerScreen(videoUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                        }
                    }
                }
            }
        }
    }
}
