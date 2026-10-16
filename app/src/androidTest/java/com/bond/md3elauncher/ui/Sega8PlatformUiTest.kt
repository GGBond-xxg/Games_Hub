package com.bond.md3elauncher.ui

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import com.bond.md3elauncher.data.*
import com.bond.md3elauncher.i18n.I18n
import com.bond.md3elauncher.system.BatchScrapeState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Isolated UI callbacks; never edits the device's platform or controller preferences. */
@RunWith(Parameterized::class)
class Sega8PlatformUiTest(private val kind: PlatformKind, private val style: ThemeStyle) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun t(key: String) = I18n.t(compose.activity, key, key)

    @Test fun addPlatformDialogScrollsToEverySegaSystem() {
        var selected: PlatformConfig? = null
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.setContent {
            GameHubTheme(themeStyle = style) {
                Surface {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        EmulatorManagerRows(emptyList(), additionalEmulatorTabs.map {
                            PlatformConfig(it.name, PlatformKind.valueOf(it.name))
                        }, { selected = it }, {})
                    }
                }
            }
        }
        compose.onNodeWithText(t("settings.emulator.add_other.action")).performScrollTo().performClick()
        compose.onNodeWithText(kind.title).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(kind, selected?.kind) }
        compose.onNodeWithText(t("common.cancel")).assertDoesNotExist()
    }

    @Test fun folderScanExternalAndInternalActionsStayReachable() {
        var folder = false
        var scanned = false
        var external = false
        var internal = false
        var back = false
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.setContent {
            var platform by remember { mutableStateOf(PlatformConfig(kind.name, kind)) }
            GameHubTheme(themeStyle = style) {
                Surface {
                    PlatformSetupScreen(platform, null, false, { back = true },
                        { folder = true; platform = platform.copy(folderUri = "content://qa/roms") },
                        { external = true; platform = platform.copy(emulatorPackage = "qa.external", emulatorName = "QA External") },
                        { platform = platform.copy(emulatorPackage = null) },
                        { internal = true; platform = platform.copy(emulatorPackage = null) },
                        { scanned = true }, {}, BatchScrapeState(), {}, {})
                }
            }
        }
        compose.onNodeWithText(t("common.using")).performScrollTo().assertIsNotEnabled()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            java.io.File(compose.activity.externalCacheDir, "qa-sega8-ui-$kind-$style-${bitmap.width}.png")
                .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onAllNodesWithText(t("common.select")).onFirst().performScrollTo().performClick()
        compose.onNodeWithText(t("common.scan")).performScrollTo().performClick()
        compose.onNodeWithText(t("common.select")).performScrollTo().performClick()
        compose.onNodeWithText(t("common.config")).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(t("common.use")).performScrollTo().performClick()
        compose.onNodeWithText(t("common.using")).assertIsNotEnabled()
        compose.onNodeWithText(t("common.config")).assertDoesNotExist()
        compose.onNodeWithText(t("common.back")).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(folder && scanned && external && internal && back) }
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}/{1}")
        fun cases() = listOf(PlatformKind.SMS, PlatformKind.GG, PlatformKind.SG1000).flatMap { kind ->
            ThemeStyle.entries.map { arrayOf<Any>(kind, it) }
        }
    }
}
