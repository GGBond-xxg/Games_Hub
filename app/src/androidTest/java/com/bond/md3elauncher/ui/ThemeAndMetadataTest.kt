package com.bond.md3elauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.content.pm.ActivityInfo
import org.junit.Before
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.md3elauncher.data.*
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.system.BatchScrapeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Isolated compositions: no MainActivity, LauncherStore writes, ROMs, credentials or network. */
@RunWith(Parameterized::class)
class ThemeAndMetadataTest(private val style: ThemeStyle) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun t(key: String) = I18n.t(InstrumentationRegistry.getInstrumentation().targetContext, key, key)

    @Before fun landscape() {
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        assertEquals("foo", com.bond.md3elauncher.system.GameScrapeSources.normalizedTitle("Foo (USA) [!]"))
    }

    @Test fun metadataEditorSavesAllFields() {
        var saved: GameMetadata? = null
        compose.setContent { GameHubTheme(themeStyle = style) { GameInfoEditor(GameMetadata(), {}, { saved = it }) } }
        val values = listOf("2001-09-10", "Strategy", "QA Developer", "QA Publisher")
        values.forEachIndexed { index, value -> compose.onAllNodes(hasSetTextAction())[index].performScrollTo().performTextReplacement(value) }
        compose.onNodeWithText(t("common.save")).performClick()
        compose.runOnIdle { assertEquals(GameMetadata(values[0], values[1], values[2], values[3]), saved) }
    }

    @Test fun batchConfirmationAndCancelKeepCallbacks() {
        var cancelled = false
        compose.setContent {
            var state by remember { mutableStateOf(BatchScrapeState()) }
            GameHubTheme(themeStyle = style) {
                BatchScrapePanel(PlatformConfig("GBA", PlatformKind.GBA, gameCount = 1), state,
                    onStart = { state = BatchScrapeState("GBA", running = true, total = 1, messageKey = "batch.running") },
                    onCancel = { cancelled = true }, scanning = false)
            }
        }
        compose.onNodeWithText(t("batch.start")).performClick()
        compose.onAllNodesWithText(t("batch.start")).onLast().performClick()
        compose.onNodeWithText(t("batch.running")).assertExists()
        compose.onNodeWithText(t("common.cancel")).performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun themeSwitchPreservesRememberedStateAndSliderSemantics() {
        var slider by mutableFloatStateOf(0.25f)
        compose.setContent {
            var selected by remember { mutableStateOf(style) }
            GameHubTheme(themeStyle = selected, onSetStyle = { selected = it }) {
                var count by remember { mutableIntStateOf(0) }
                Column(Modifier.styleSurface(androidx.compose.ui.graphics.RectangleShape)) {
                    ThemeStyleSetting()
                    HubButton(onClick = { count++ }) { Text("count=$count") }
                    HubSlider(slider, { slider = it }, Modifier)
                }
            }
        }
        compose.onNodeWithText("count=0").performClick()
        ThemeStyle.entries.forEach {
            compose.onNodeWithText(t("theme.${it.name.lowercase()}")).performClick()
            compose.onNodeWithText("count=1").assertExists()
            compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.6f) }
            compose.runOnIdle { assertEquals(0.6f, slider, 0.001f) }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.runOnIdle { assertEquals(0.75f, slider, 0.001f) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun styles() = ThemeStyle.entries.map { arrayOf(it) }
    }
}
