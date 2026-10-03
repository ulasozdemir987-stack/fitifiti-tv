package com.fitifiti.tv.ui.player

import androidx.compose.runtime.Composable
import com.fitifiti.tv.ui.PlayRequest

@Composable fun PlayerScreen(req: PlayRequest, onClose: () -> Unit) {}
@Composable fun LivePlayerScreen(channelId: Int, list: List<Int>, onClose: () -> Unit) {}
