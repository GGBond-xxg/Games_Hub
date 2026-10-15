package com.bond.md3elauncher.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Dedicated preferences only: never reads/writes the user's launcher store or language file. */
class LauncherStorePersistenceTest {
    @Test fun settingsArtworkAndOrderingSurviveStoreRecreation() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-store-${UUID.randomUUID()}"
        val preferences = target.getSharedPreferences(name, Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(requestedName: String, mode: Int) = preferences
        }
        try {
            val store = LauncherStore(isolated)
            val platforms = store.loadPlatforms().map {
                if (it.id == "GBA") it.copy(folderUri = "content://qa/roms", emulatorPackage = "internal:gba", gameCount = 7) else it
            }
            store.savePlatforms(platforms)
            store.setFavorite("game-1", true)
            store.setAndroidGame("app-1", true)
            store.pushRecent("game-1")
            store.pushRecent("game-2")
            store.pushRecent("game-1")
            store.saveItemOrder("GBA", listOf("game-2", "game-1"))
            store.saveItemOverride(ItemOverride("game-1", "Custom", "preview.img", "grid.img"))
            store.saveThemeStyle(ThemeStyle.GLASS)
            store.saveGlassSettings(GlassSettings(8f, 12f, 0.7f))
            store.saveThemeMode(ThemeMode.DARK)
            store.saveThemeColor(ThemeColor.PINK)
            store.saveBackgroundMotion(true)
            store.saveUseDynamicColor(false)
            store.saveSafeMargins(SafeMarginSettings(12, 36))
            store.saveLandscapeMode(LandscapeMode.RIGHT)
            store.saveLauncherLayoutMode(LauncherLayoutMode.GRID)
            store.saveLanguageMode("zh-Hant")
            store.markHomePromptDone()
            store.saveScraperSettings(ScraperSettings(useLibretro = false, theGamesDbApiKey = " test-placeholder "))
            val reopened = LauncherStore(isolated)
            assertEquals(platforms, reopened.loadPlatforms())
            assertEquals(setOf("game-1"), reopened.loadFavorites())
            assertEquals(setOf("app-1"), reopened.loadAndroidGames())
            assertEquals(listOf("game-1", "game-2"), reopened.loadRecent())
            assertEquals(listOf("game-2", "game-1"), reopened.loadItemOrders()["GBA"])
            val artwork = reopened.loadItemOverrides().getValue("game-1")
            assertEquals("preview.img", artwork.previewImagePath)
            assertEquals("grid.img", artwork.gridImagePath)
            assertEquals(ThemeStyle.GLASS, reopened.loadThemeStyle())
            assertEquals(GlassSettings(8f, 12f, 0.7f), reopened.loadGlassSettings())
            assertEquals(ThemeMode.DARK, reopened.loadThemeMode())
            assertEquals(ThemeColor.PINK, reopened.loadThemeColor())
            assertTrue(reopened.loadBackgroundMotion())
            assertFalse(reopened.loadUseDynamicColor())
            assertEquals(SafeMarginSettings(12, 36), reopened.loadSafeMargins())
            assertEquals(LandscapeMode.RIGHT, reopened.loadLandscapeMode())
            assertEquals(LauncherLayoutMode.GRID, reopened.loadLauncherLayoutMode())
            assertEquals("zh-Hant", reopened.loadLanguageMode())
            assertFalse(reopened.shouldShowHomePrompt())
            assertEquals("test-placeholder", reopened.loadScraperSettings().theGamesDbApiKey)
            assertFalse(reopened.loadScraperSettings().useLibretro)
            reopened.setFavorite("game-1", false)
            reopened.setAndroidGame("app-1", false)
            assertTrue(store.loadFavorites().isEmpty())
            assertTrue(store.loadAndroidGames().isEmpty())
        } finally { target.deleteSharedPreferences(name) }
    }
}
