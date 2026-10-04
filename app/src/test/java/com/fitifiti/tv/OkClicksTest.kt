package com.fitifiti.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.fitifiti.tv.ui.components.okClicks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** OK kısa basış = tıklama, basılı tutma = seçenekler (kumanda uzun basış işareti göndermese de) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalTestApi::class)
class OkClicksTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shortAndLong() {
        var clicks = 0; var longs = 0
        rule.setContent {
            val f = remember { FocusRequester() }
            LaunchedEffect(Unit) { f.requestFocus() }
            Box(Modifier.size(50.dp).focusRequester(f).okClicks({ clicks++ }, { longs++ }).focusable().testTag("b"))
        }
        rule.waitForIdle()
        rule.onNodeWithTag("b").performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(100); keyUp(Key.DirectionCenter) }
        assertEquals(1, clicks); assertEquals(0, longs)
        rule.onNodeWithTag("b").performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(800); keyUp(Key.DirectionCenter) }
        assertEquals(1, clicks); assertEquals(1, longs)
    }

    /** Asıl kart (tv-material Surface) üzerinde: tekrar göndermeyen uzun basış da seçenekleri açmalı */
    @Test fun onCard() {
        var clicks = 0; var longs = 0
        rule.setContent {
            com.fitifiti.tv.ui.components.LandscapeCard("x", null, null, { clicks++ }, Modifier.testTag("c"), onLongClick = { longs++ })
        }
        rule.waitForIdle()
        rule.onNodeWithTag("c").onChildren().filter(isFocusable()).onFirst().requestFocus()
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(100); keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals("kısa", 1, clicks); assertEquals(0, longs)
        rule.onRoot().performKeyInput { keyDown(Key.DirectionCenter); advanceEventTime(900); keyUp(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals("uzun", 1, clicks); assertEquals(1, longs)
    }
}
