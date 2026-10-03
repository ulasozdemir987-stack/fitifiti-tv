package com.fitifiti.tv

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Text
import com.fitifiti.tv.ui.components.Btn
import com.fitifiti.tv.ui.components.HeroArt
import com.fitifiti.tv.ui.screens.DetailScaffold
import com.fitifiti.tv.ui.screens.detailHead
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Kumanda gezinmesi: detay sayfası "Oynat"a odaklanınca kaymamalı, aşağıdan geri gelince en üste dönmeli */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television", sdk = [34])
@OptIn(ExperimentalTestApi::class)
class FocusProbeTest {
    @get:Rule val rule = createComposeRule()

    @Before fun leanback() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>().packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    @Test fun detailHeadStaysAtTop() {
        var list: androidx.compose.foundation.lazy.LazyListState? = null
        rule.setContent {
            DetailScaffold(HeroArt(null, null, null, null)) {
                item(key = "head") {
                    Column(Modifier.detailHead().heightIn(min = (540 * 0.86f).dp).padding(48.dp)) {
                        Spacer(Modifier.weight(1f, fill = false).height(300.dp))
                        Row { Btn("Oynat", {}, Modifier.testTag("play")); Btn("Listem", {}, Modifier.testTag("list")) }
                    }
                }
                items(6) { r -> LazyRow { items(8) { i -> Btn("r$r-$i", {}, Modifier.padding(8.dp).height(120.dp).testTag("r$r-$i")) } } }
            }
        }
        // DetailScaffold'un listesi (ilk LazyColumn) ölçüm için semantik ağacından değil, kaydırma konumundan okunur
        rule.onNodeWithTag("play").requestFocus(); rule.waitForIdle()
        val playTop0 = rule.onNodeWithTag("play").fetchSemanticsNode().boundsInRoot.top
        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }; rule.waitForIdle()
        rule.onNodeWithTag("list").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }; rule.waitForIdle()
        rule.onNodeWithTag("play").assertIsFocused()
        assertEquals("Oynat/Listem arası gezinince sayfa kaymamalı", playTop0, rule.onNodeWithTag("play").fetchSemanticsNode().boundsInRoot.top, 1f)
        println("PROBE playTop=$playTop0")
        // aşağı in, sonra geri çık: başlık en üste dönmeli
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        val scrolled = rule.onNodeWithTag("play").fetchSemanticsNode().boundsInRoot.top
        println("PROBE after downs playTop=$scrolled")
        repeat(3) { rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }; rule.waitForIdle() }
        rule.mainClock.advanceTimeBy(1500); rule.waitForIdle()
        rule.onNodeWithTag("play").assertIsFocused()
        assertEquals("geri çıkınca başlık eski yerinde olmalı", playTop0, rule.onNodeWithTag("play").fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
