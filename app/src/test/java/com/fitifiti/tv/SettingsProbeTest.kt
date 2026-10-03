package com.fitifiti.tv

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Text
import com.fitifiti.tv.ui.components.Btn
import com.fitifiti.tv.ui.components.SettingRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-land-television", sdk = [34])
@OptIn(ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
class SettingsProbeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun downFromTopBarIcon() {
        Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>().packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
        val content = androidx.compose.ui.focus.FocusRequester()
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().focusRequester(content).focusGroup()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 100.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Ayarlar") }
                    items(14) { i -> SettingRow("Satır $i", onClick = {}, modifier = Modifier.fillMaxWidth(0.7f).testTag("row$i")) }
                }
                }
                Row(Modifier.focusProperties { down = content }.fillMaxWidth().padding(20.dp)) {
                    Btn("Ana Sayfa", {}, Modifier.testTag("home")); Spacer(Modifier.weight(1f)); Btn("Ayarlar", {}, Modifier.testTag("gear"))
                }
            }
        }
        rule.onNodeWithTag("gear").requestFocus(); rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        val focused = (0 until 14).firstOrNull { runCatching { rule.onNodeWithTag("row$it").assertIsFocused() }.isSuccess }
        println("PROBE after down from gear: row=$focused")
        repeat(12) { rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle() }
        val f2 = (0 until 14).firstOrNull { runCatching { rule.onNodeWithTag("row$it").assertIsFocused() }.isSuccess }
        println("PROBE after 12 more downs: row=$f2")
        org.junit.Assert.assertEquals(0, focused)
        org.junit.Assert.assertEquals(12, f2)
    }
}
