package com.fitifiti.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.tv.material3.Text
import com.fitifiti.tv.ui.ScreenLayer
import com.fitifiti.tv.ui.components.Btn
import com.fitifiti.tv.ui.rememberFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Detaydan tek geri basışında alttaki ekran açılmalı ve odak girilen karta dönmeli */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television", sdk = [34])
@OptIn(ExperimentalTestApi::class)
class BackFocusTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun oneBackRestoresCard() {
        val stack = mutableStateListOf("home")
        rule.setContent {
            BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
            Box(Modifier.fillMaxSize()) {
                stack.toList().forEachIndexed { i, s ->
                    key(s) {
                        ScreenLayer(i == stack.lastIndex) {
                            if (s == "home") Column {
                                Row { Btn("Ana Sayfa", {}, Modifier.testTag("tab0").rememberFocus()); Btn("Filmler", {}, Modifier.testTag("tab1").rememberFocus()) }
                                LazyRow { items(10) { n -> Btn("kart$n", { stack.add("detail") }, Modifier.padding(6.dp).testTag("card$n")) } }
                            } else Column { Btn("Oynat", {}, Modifier.testTag("play")); Text("detay") }
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag("card0").requestFocus(); rule.waitForIdle()
        repeat(3) { rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle() }
        rule.onNodeWithTag("card3").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        rule.onNodeWithTag("play").requestFocus(); rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        val tags = listOf("tab0","tab1") + (0 until 10).map { "card$it" }
        rule.mainClock.autoAdvance = false
        for (t in 0 until 60) {
            rule.mainClock.advanceTimeBy(50)
            val f = tags.firstOrNull { tg -> runCatching { rule.onNodeWithTag(tg).assertIsFocused() }.isSuccess }
            if (f != null) { println("PROBE focused $f at ${(t + 1) * 50} ms"); break }
        }
        rule.mainClock.autoAdvance = true
        println("PROBE stack=${stack.toList()}")
        println("PROBE focused=" + tags.firstOrNull { t -> runCatching { rule.onNodeWithTag(t).assertIsFocused() }.isSuccess })
        rule.mainClock.advanceTimeBy(2000); rule.waitForIdle()
        println("PROBE focused later=" + tags.firstOrNull { t -> runCatching { rule.onNodeWithTag(t).assertIsFocused() }.isSuccess })
        org.junit.Assert.assertEquals(1, stack.size)
        rule.onNodeWithTag("card3").assertIsFocused()
    }
}
