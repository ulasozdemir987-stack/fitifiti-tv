package com.fitifiti.tv.ui.screens

import androidx.compose.runtime.Composable
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series

@Composable fun MovieDetailScreen(m: Movie) {}
@Composable fun SeriesDetailScreen(s: Series, focusEpisodeId: String?) {}
@Composable fun CategoryScreen(kind: String, categoryId: String?, genre: String?) {}
@Composable fun LiveScreen() {}
@Composable fun ListemScreen() {}
@Composable fun SearchScreen() {}
@Composable fun SettingsScreen(onProfiles: () -> Unit, onEditAccount: (String) -> Unit, onAddAccount: () -> Unit) {}
