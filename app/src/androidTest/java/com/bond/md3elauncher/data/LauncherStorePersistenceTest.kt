package com.bond.md3elauncher.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Dedicated preferences only: never reads/writes the user's launcher store or language file. */
class LauncherStorePersistenceTest {
    @Test fun languageFileRemainsTheSourceAcrossContextRecreation() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-language-${UUID.randomUUID()}"
        val preferences = target.getSharedPreferences(name, Context.MODE_PRIVATE)
        val directory = java.io.File(target.cacheDir, name).apply { mkdirs() }
        fun isolated() = object : ContextWrapper(target) {
            override fun getSharedPreferences(requestedName: String, mode: Int) = preferences
            override fun getFilesDir() = directory
        }
        try {
            com.bond.md3elauncher.i18n.I18n.setLanguageOverride(isolated(), "zh-Hant")
            preferences.edit().clear().commit()
            assertEquals("zh-Hant", com.bond.md3elauncher.i18n.I18n.savedLanguageMode(isolated()))
            com.bond.md3elauncher.i18n.I18n.setLanguageOverride(isolated(), "en")
            assertEquals("en", com.bond.md3elauncher.i18n.I18n.savedLanguageMode(isolated()))
        } finally {
            target.deleteSharedPreferences(name)
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }

    @Test fun libraryRecoveryAndControllerBindingsSurvivePreferenceWrites() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "qa-store-${UUID.randomUUID()}"
        val preferences = target.getSharedPreferences(name, Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(target) {
            override fun getSharedPreferences(requestedName: String, mode: Int) = preferences
        }
        try {
            val store = LauncherStore(isolated)
            val first = listOf(GameItem("qa-1", "GBA", "GBA", "QA", "qa.gba", "gba", "content://qa/1", addedAt = 1))
            val second = first + first.first().copy(id = "qa-2")
            store.saveGames(first)
            store.saveGames(second)
            store.saveGames(second) // Same-value writes must retain the previous valid snapshot.
            assertEquals(second, LauncherStore(isolated).loadGames())
            preferences.edit().putString("games", "broken").commit()
            assertEquals(first, LauncherStore(isolated).loadGames())
            store.saveGames(second) // An invalid prior document must not replace the recovery snapshot.
            preferences.edit().putString("games", "broken again").commit()
            assertEquals(first, LauncherStore(isolated).loadGames())

            val action = com.bond.md3elauncher.emulator.ControllerShortcutAction.QUICK_SAVE
            val keys = setOf(android.view.KeyEvent.KEYCODE_BUTTON_L1, android.view.KeyEvent.KEYCODE_BUTTON_A)
            val saved = com.bond.md3elauncher.emulator.ControllerShortcutSettings.saveBinding(isolated, action, keys)
            val loaded = com.bond.md3elauncher.emulator.ControllerShortcutSettings.load(isolated)
            assertEquals(saved, loaded)
            assertEquals(keys, loaded.keysFor(action))
            assertEquals(action, com.bond.md3elauncher.emulator.ControllerShortcutSettings.findTriggeredAction(
                loaded, keys, android.view.KeyEvent.KEYCODE_BUTTON_A, emptySet()))
            assertEquals("circle", target.getString(com.bond.md3elauncher.R.string.touch_style_circle))
            assertEquals("CodeBreaker/GameShark", target.getString(com.bond.md3elauncher.R.string.cheat_type_default))
        } finally { target.deleteSharedPreferences(name) }
    }

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
