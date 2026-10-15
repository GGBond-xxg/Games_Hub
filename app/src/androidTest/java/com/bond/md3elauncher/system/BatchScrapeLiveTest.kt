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

/** Live pipeline smoke test by title only; no ROM, LauncherStore write or saved credentials. */
class BatchScrapeLiveTest {
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
