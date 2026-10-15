package com.bond.md3elauncher.system

import androidx.test.platform.app.InstrumentationRegistry
import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.ScraperSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import org.junit.Before
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import com.bond.md3elauncher.ui.EditItemPage
import com.bond.md3elauncher.ui.EditTarget
import com.bond.md3elauncher.ui.GameHubTheme
import com.bond.md3elauncher.data.ThemeStyle
import com.bond.md3elauncher.i18n.I18n

/** Live pipeline smoke test by title only; no ROM, LauncherStore write or saved credentials. */
class BatchScrapeLiveTest {
    @Test(timeout = 90_000) fun previewCanUseEnglishCoverWithoutOverwritingGrid() {
        val key = InstrumentationRegistry.getArguments().getString("tgdb_test_key").orEmpty()
        assumeTrue("Live API key not supplied", key.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun t(key: String) = I18n.t(context, key, key)
        var savedPreview: String? = null
        var savedGrid: String? = null
        val target = EditTarget("qa-incredibox-ui", "Incredibox", "Incredibox", null, null, null, null, "Android apps")
        compose.setContent {
            GameHubTheme(themeStyle = ThemeStyle.GLASS) {
                EditItemPage(target, {}, ScraperSettings(useLibretro = false, theGamesDbApiKey = key), {},
                    onSave = { _, preview, grid -> savedPreview = preview; savedGrid = grid })
            }
        }
        try {
            compose.onAllNodesWithText(t("edit.choose_online")).onFirst().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText(t("edit.no_artwork_found")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(t("edit.artwork_cover")).performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("Incredibox").fetchSemanticsNodes().size >= 2 }
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            try {
                File(compose.activity.externalCacheDir, "qa-incredibox-results.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { bitmap.recycle() }
            compose.onAllNodesWithText("Incredibox").onLast().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText(t("edit.choose_online")).fetchSemanticsNodes().size == 2 }
            compose.onNodeWithText(t("common.save")).performClick()
            compose.runOnIdle { assertNotNull(savedPreview); assertNull(savedGrid) }
        } finally { savedPreview?.let { android.net.Uri.parse(it).path?.let { path -> File(path).delete() } } }
    }
    @Test(timeout = 90_000) fun englishManualCoverSearchAndAuthenticationReport() {
        val key = InstrumentationRegistry.getArguments().getString("tgdb_test_key").orEmpty()
        assumeTrue("Live API key not supplied", key.isNotBlank())
        val scraper = CoverScraper(InstrumentationRegistry.getInstrumentation().targetContext)
        val settings = ScraperSettings(useLibretro = false, theGamesDbApiKey = key)
        val covers = scraper.searchCovers("Incredibox", "Android apps", null, settings, CoverArtworkType.BOX_ART)
        assertTrue(covers.any { it.title == "Incredibox" })
        assertNull(scraper.lastErrorKey)
        val path = scraper.downloadCandidate(covers.first(), "qa-incredibox-cover")
        assertNotNull(path)
        try {
            val bitmap = android.graphics.BitmapFactory.decodeFile(path)
            assertNotNull(bitmap)
            bitmap.recycle()
        } finally { path?.let { File(it).delete() } }
        assertTrue(scraper.searchCovers("incredibox", "安卓应用", null, settings, CoverArtworkType.SCREENSHOT).isEmpty())
        assertNull(scraper.lastErrorKey)
        scraper.searchCovers("Incredibox", "Android apps", null,
            settings.copy(theGamesDbApiKey = "invalid-test-placeholder"), CoverArtworkType.BOX_ART)
        assertEquals("cover.report.tgdb_auth", scraper.lastErrorKey)
        assertFalse(scraper.lastReport.contains(key))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepTestVisible() = compose.runOnUiThread {
        compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    @Test(timeout = 150_000) fun fillsMissingMediaAndMetadataByTitle() = runBlocking {
        val key = InstrumentationRegistry.getArguments().getString("tgdb_test_key").orEmpty()
        assumeTrue("Live API key not supplied", key.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scraper = GameScraper(context)
        val settings = ScraperSettings(useLibretro = false, theGamesDbApiKey = key)
        val files = mutableListOf<String>()
        try {
            for (title in listOf("Advance Wars", "高级战争")) {
                val game = GameItem("qa-title-only", "GBA", "GBA", title, "", "gba", "")
                val result = scraper.scrape(game, ScrapeNeeds(true, true, true), settings, "en")
                result.cover?.let(files::add); result.screenshot?.let(files::add)
                assertTrue(result.metadata.isComplete)
                assertEquals("2001-09-10", result.metadata.releaseDate)
                assertNotNull(result.cover); assertNotNull(result.screenshot)
                assertNotEquals(result.cover, result.screenshot)
                listOf(result.cover, result.screenshot).forEach {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(it)
                    assertNotNull(bitmap)
                    assertTrue(bitmap.width > 0 && bitmap.height > 0)
                    bitmap.recycle()
                }
            }
            val broad = GameItem("qa-title-only", "GBA", "GBA", "星之卡比", "", "gba", "")
            val result = scraper.scrape(broad, ScrapeNeeds(true, true, true), settings, "en")
            result.cover?.let(files::add); result.screenshot?.let(files::add)
            assertTrue(result.metadata.isEmpty)
            assertNull(result.cover); assertNull(result.screenshot)
        } finally { files.forEach { File(it).delete() } }
    }
}
