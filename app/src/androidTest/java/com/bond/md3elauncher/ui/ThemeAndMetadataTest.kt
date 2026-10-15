package com.bond.md3elauncher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
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
        compose.runOnUiThread {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
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

    @Test fun fieldSaveCommitsAndSwitchRetainsAccessibleAction() {
        var saved = ""
        var toggled = false
        compose.setContent {
            var value by remember { mutableStateOf("") }
            var motion by remember { mutableStateOf(false) }
            GameHubTheme(themeStyle = style, backgroundMotion = motion, onSetBackgroundMotion = { motion = it }) {
                Column {
                    ScraperTextField(onSave = { saved = value }, label = "Test credential", value = value,
                        onValueChange = { value = it }, placeholder = "", isSecret = true)
                    HubSwitch(toggled, { toggled = it }, Modifier.testTag("toggle"))
                    ThemeStyleSetting()
                }
            }
        }
        compose.onNodeWithText(t("settings.input.edit")).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("test-placeholder")
        val inputHeight = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot.height
        val buttonHeight = compose.onNodeWithText(t("settings.input.save")).fetchSemanticsNode().boundsInRoot.height
        assertEquals(inputHeight, buttonHeight, 2f)
        compose.onNodeWithText(t("settings.input.save")).performClick()
        compose.runOnIdle { assertEquals("test-placeholder", saved) }
        compose.onNodeWithTag("toggle").performClick()
        compose.runOnIdle { assertTrue(toggled) }
        if (style != ThemeStyle.M3) {
            compose.onNodeWithText(t("theme.background_motion")).performClick()
            compose.onNodeWithText(t("theme.background_motion")).assertIsOn()
            compose.onNodeWithText(t("theme.background_motion")).performClick()
            compose.onNodeWithText(t("theme.background_motion")).assertIsOff()
        } else compose.onNodeWithText(t("theme.background_motion")).assertDoesNotExist()
    }

    @Test fun artworkEditorAndSearchHaveOneQueryAction() {
        val target = EditTarget("qa-artwork", "Kirby", "Kirby", null, null, null, null, "GBA")
        compose.setContent {
            GameHubTheme(themeStyle = style) {
                EditItemPage(target, {}, ScraperSettings(useLibretro = false), {}, onSave = { _, _, _ -> })
            }
        }
        compose.onAllNodesWithText(t("edit.choose_online")).assertCountEquals(2)
        screenshot("edit")
        compose.onAllNodesWithText(t("edit.choose_online")).onFirst().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(t("common.search")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(t("common.search")).assertCountEquals(1)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithText(t("edit.artwork_screenshot")).assertIsSelected()
        compose.onNodeWithText(t("edit.artwork_cover")).performClick()
        compose.onNodeWithText(t("edit.artwork_cover")).assertIsSelected()
        compose.onNodeWithText(t("edit.artwork_screenshot")).performClick()
        compose.onNodeWithText(t("edit.artwork_screenshot")).assertIsSelected()
        compose.onNode(hasSetTextAction()).performTextReplacement("星之卡比")
        compose.onNodeWithText(t("common.search")).performClick()
        compose.waitForIdle()
        // Hide the IME so the capture covers the actual search layout.
        compose.runOnUiThread { compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0) }
        screenshot("search")
    }

    private fun screenshot(page: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val width = compose.activity.resources.configuration.screenWidthDp
        val file = java.io.File(compose.activity.externalCacheDir, "qa-$page-$style-$width.png")
        try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun versionEntryScrollsLongNotesAndKeepsDismissVisible() {
        val checked = kotlinx.coroutines.CompletableDeferred<com.bond.md3elauncher.system.ReleaseInfo>()
        compose.setContent {
            GameHubTheme(themeStyle = style) { UpdateSettingsEntry(checkForUpdate = { checked.await() }) }
        }
        compose.onNodeWithText(t("update.version"), substring = true).performClick()
        compose.onNodeWithText(t("update.checking")).assertIsDisplayed()
        compose.runOnIdle {
            checked.complete(com.bond.md3elauncher.system.ReleaseInfo("99.0.0",
                (1..80).joinToString("\n") { "Release note $it" }, "https://github.com/GGBond-xxg/Games_Hub/releases", null))
        }
        compose.waitForIdle()
        compose.onNodeWithText(t("common.cancel")).assertIsDisplayed()
        compose.onNodeWithText(t("update.github")).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        val range = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        compose.runOnIdle {
            assertTrue("Long release notes must be scrollable", range.maxValue() > 0f)
            assertEquals(range.maxValue(), range.value(), 1f)
        }
        screenshot("version")
        compose.onNodeWithText(t("common.cancel")).performClick()
        compose.onNodeWithText(t("update.title")).assertDoesNotExist()
        // Opening again and system/controller Back must dismiss without losing the entry.
        compose.onNodeWithText(t("update.version"), substring = true).performClick()
        compose.onNodeWithText(t("update.title")).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText(t("update.title")).assertDoesNotExist()
        compose.onNodeWithText(t("update.version"), substring = true).assertIsDisplayed()
    }

    @Test fun versionCheckErrorCanRetryWithoutClosingDialog() {
        var attempts = 0
        compose.setContent {
            GameHubTheme(themeStyle = style) {
                UpdateSettingsEntry(checkForUpdate = {
                    attempts++
                    if (attempts == 1) throw java.io.IOException("Offline test")
                    com.bond.md3elauncher.system.ReleaseInfo(com.bond.md3elauncher.BuildConfig.VERSION_NAME, "", "", null)
                })
            }
        }
        compose.onNodeWithText(t("update.version"), substring = true).performClick()
        compose.onNodeWithText(t("update.network_error")).assertIsDisplayed()
        compose.onNodeWithText(t("update.retry")).performClick()
        compose.onNodeWithText(t("update.latest"), substring = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithText(t("common.cancel")).performClick()
    }

    @Test fun backupEntryUsesOneScrollContainerAndCanCancel() {
        compose.setContent { GameHubTheme(themeStyle = style) { AboutAndBackupSettings(isScanning = false) } }
        compose.onNodeWithText(t("settings.backup.title")).performClick()
        compose.onNodeWithText(t("settings.backup.export")).assertIsDisplayed()
        compose.onNodeWithText(t("settings.backup.restore")).assertIsDisplayed()
        compose.onNodeWithText(t("common.cancel")).performClick()
        compose.onNodeWithText(t("settings.backup.export")).assertDoesNotExist()
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun styles() = ThemeStyle.entries.map { arrayOf(it) }
    }
}
