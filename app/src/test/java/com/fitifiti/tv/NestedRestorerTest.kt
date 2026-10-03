package com.fitifiti.tv

import android.content.pm.PackageManager
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.fitifiti.tv.ui.ScreenLayer
import com.fitifiti.tv.ui.components.Btn
import com.fitifiti.tv.ui.screens.mainContentFocus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ana ekran yapısı (ekran katmanı + üst çubuk + şeritler): yön tuşlarıyla gezinirken çökmemeli (2.5.0'da çöküyordu) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television", sdk = [34])
@OptIn(ExperimentalTestApi::class)
class NestedRestorerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun navigateWithoutCrash() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>().packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        val content = FocusRequester()
        rule.setContent {
            ScreenLayer(true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().mainContentFocus(content)) {
                        LazyColumn(contentPadding = PaddingValues(top = 90.dp)) {
                            items(8) { r -> LazyRow { items(12) { i -> Btn("r$r-$i", {}, Modifier.padding(6.dp).testTag("r$r-$i")) } } }
                        }
                    }
                    Row(Modifier.focusProperties { down = content }.padding(16.dp)) {
                        Btn("Ana", {}, Modifier.testTag("t0")); Btn("Film", {}, Modifier.testTag("t1")); Spacer(Modifier.width(400.dp)); Btn("Ayar", {}, Modifier.testTag("gear"))
                    }
                }
            }
        }
        rule.onNodeWithTag("r0-0").requestFocus(); rule.waitForIdle()
        val keys = listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionDown, Key.DirectionDown, Key.DirectionRight, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp,
            Key.DirectionUp, Key.DirectionDown, Key.DirectionDown, Key.DirectionRight, Key.DirectionUp, Key.DirectionUp, Key.DirectionUp, Key.DirectionRight, Key.DirectionDown, Key.DirectionDown)
        repeat(4) { keys.forEach { k -> rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() } }
        rule.onNodeWithTag("gear").requestFocus(); rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        val f = (0 until 8).flatMap { r -> (0 until 12).map { "r$r-$it" } }.firstOrNull { runCatching { rule.onNodeWithTag(it).assertIsFocused() }.isSuccess }
        println("PROBE after gear down: $f")
        org.junit.Assert.assertNotNull("üst çubuktan ↓ içeriğe inmeli", f)
    }
}
