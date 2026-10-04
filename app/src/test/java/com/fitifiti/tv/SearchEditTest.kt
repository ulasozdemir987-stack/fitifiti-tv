package com.fitifiti.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.fitifiti.tv.ui.components.Btn
import com.fitifiti.tv.ui.components.TvTextField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ara simgesine OK: arama kutusu yazma moduna geçip odak almalı (kutuda klavye açılmıyordu) */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-television", sdk = [34])
@OptIn(ExperimentalTestApi::class)
class SearchEditTest {
    @get:Rule val rule = createComposeRule()

    @Test fun kickStartsEditing() {
        var kick by mutableIntStateOf(0)
        rule.setContent {
            val f = remember { FocusRequester() }
            LaunchedEffect(Unit) { f.requestFocus() }
            var q by remember { mutableStateOf("") }
            Column {
                Btn("Ara", { kick++ }, Modifier.focusRequester(f).testTag("icon"))
                TvTextField(q, { q = it }, "", placeholder = "ara", startEditing = kick)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("icon").performKeyInput { pressKey(Key.DirectionCenter) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertIsFocused()
    }
}
