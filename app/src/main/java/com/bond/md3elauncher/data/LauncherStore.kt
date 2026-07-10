package com.bond.md3elauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LauncherStore(context: Context) {
    private val prefs = context.getSharedPreferences("md3e_launcher_store", Context.MODE_PRIVATE)

    fun loadPlatforms(): List<PlatformConfig> {
        val raw = prefs.getString(KEY_PLATFORMS, null) ?: return defaultPlatforms()
        val loaded = runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val kind = PlatformKind.valueOf(obj.getString("kind"))
                    add(
                        PlatformConfig(
                            id = obj.getString("id"),
                            kind = kind,
                            folderUri = obj.optStringOrNull("folderUri"),
                            emulatorPackage = obj.optStringOrNull("emulatorPackage"),
                            emulatorName = obj.optStringOrNull("emulatorName"),
                            gameCount = obj.optInt("gameCount", 0),
                            lastScanAt = obj.optLong("lastScanAt", 0L)
                        )
                    )
                }
            }
        }.getOrElse { defaultPlatforms() }
        return mergeDefaultPlatforms(loaded)
    }

    fun savePlatforms(platforms: List<PlatformConfig>) {
        val arr = JSONArray()
        platforms.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("kind", p.kind.name)
                    .putNullable("folderUri", p.folderUri)
                    .putNullable("emulatorPackage", p.emulatorPackage)
                    .putNullable("emulatorName", p.emulatorName)
                    .put("gameCount", p.gameCount)
                    .put("lastScanAt", p.lastScanAt)
            )
        }
        prefs.edit().putString(KEY_PLATFORMS, arr.toString()).apply()
    }

    fun loadGames(): List<GameItem> {
        val raw = prefs.getString(KEY_GAMES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        GameItem(
                            id = obj.getString("id"),
                            platformId = obj.getString("platformId"),
                            platformTitle = obj.getString("platformTitle"),
                            title = obj.getString("title"),
                            fileName = obj.getString("fileName"),
                            extension = obj.getString("extension"),
                            uri = obj.getString("uri"),
                            addedAt = obj.optLong("addedAt", 0L),
                            serial = obj.optStringOrNull("serial"),
                            coverPath = obj.optStringOrNull("coverPath"),
                            backgroundPath = obj.optStringOrNull("backgroundPath")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveGames(games: List<GameItem>) {
        val arr = JSONArray()
        games.forEach { g ->
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("platformId", g.platformId)
                    .put("platformTitle", g.platformTitle)
                    .put("title", g.title)
                    .put("fileName", g.fileName)
                    .put("extension", g.extension)
                    .put("uri", g.uri)
                    .put("addedAt", g.addedAt)
                    .putNullable("serial", g.serial)
                    .putNullable("coverPath", g.coverPath)
                    .putNullable("backgroundPath", g.backgroundPath)
            )
        }
        prefs.edit().putString(KEY_GAMES, arr.toString()).apply()
    }

    fun loadFavorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun setFavorite(key: String, favorite: Boolean) {
        val next = loadFavorites().toMutableSet()
        if (favorite) next.add(key) else next.remove(key)
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    fun loadAndroidGames(): Set<String> = prefs.getStringSet(KEY_ANDROID_GAMES, emptySet()) ?: emptySet()

    fun setAndroidGame(key: String, enabled: Boolean) {
        val next = loadAndroidGames().toMutableSet()
        if (enabled) next.add(key) else next.remove(key)
        prefs.edit().putStringSet(KEY_ANDROID_GAMES, next).apply()
    }

    fun loadRecent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }.getOrElse { emptyList() }
    }

    fun pushRecent(gameId: String) {
        val next = loadRecent().toMutableList()
        next.remove(gameId)
        next.add(0, gameId)
        val arr = JSONArray()
        next.take(20).forEach { arr.put(it) }
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply()
    }

    fun loadItemOverrides(): Map<String, ItemOverride> {
        val raw = prefs.getString(KEY_ITEM_OVERRIDES, null) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            buildMap {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val key = obj.getString("key")
                    put(
                        key,
                        ItemOverride(
                            key = key,
                            title = obj.optStringOrNull("title"),
                            imagePath = obj.optStringOrNull("imagePath")
                        )
                    )
                }
            }
        }.getOrElse { emptyMap() }
    }

    fun saveItemOverride(override: ItemOverride) {
        val next = loadItemOverrides().toMutableMap()
        val title = override.title?.trim()?.takeIf { it.isNotBlank() }
        val imagePath = override.imagePath?.takeIf { it.isNotBlank() }
        if (title == null && imagePath == null) {
            next.remove(override.key)
        } else {
            next[override.key] = override.copy(title = title, imagePath = imagePath)
        }
        saveItemOverrides(next.values.toList())
    }

    private fun saveItemOverrides(overrides: List<ItemOverride>) {
        val arr = JSONArray()
        overrides.forEach { item ->
            arr.put(
                JSONObject()
                    .put("key", item.key)
                    .putNullable("title", item.title)
                    .putNullable("imagePath", item.imagePath)
            )
        }
        prefs.edit().putString(KEY_ITEM_OVERRIDES, arr.toString()).apply()
    }

    fun shouldShowHomePrompt(): Boolean = !prefs.getBoolean(KEY_HOME_PROMPT_DONE, false)

    fun markHomePromptDone() {
        prefs.edit().putBoolean(KEY_HOME_PROMPT_DONE, true).apply()
    }

    fun loadLandscapeMode(): LandscapeMode {
        val raw = prefs.getString(KEY_LANDSCAPE_MODE, LandscapeMode.AUTO.name) ?: LandscapeMode.AUTO.name
        return runCatching { LandscapeMode.valueOf(raw) }.getOrDefault(LandscapeMode.AUTO)
    }

    fun saveLandscapeMode(mode: LandscapeMode) {
        prefs.edit().putString(KEY_LANDSCAPE_MODE, mode.name).apply()
    }

    fun loadThemeMode(): ThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun loadUseDynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)

    fun saveUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun loadSafeMargins(): SafeMarginSettings = SafeMarginSettings(
        leftDp = prefs.getInt(KEY_SAFE_MARGIN_LEFT, SafeMarginSettings.DEFAULT_DP),
        rightDp = prefs.getInt(KEY_SAFE_MARGIN_RIGHT, SafeMarginSettings.DEFAULT_DP)
    ).clamped()

    fun saveSafeMargins(settings: SafeMarginSettings) {
        val clean = settings.clamped()
        prefs.edit()
            .putInt(KEY_SAFE_MARGIN_LEFT, clean.leftDp)
            .putInt(KEY_SAFE_MARGIN_RIGHT, clean.rightDp)
            .apply()
    }

    fun resetSafeMargins() {
        saveSafeMargins(SafeMarginSettings())
    }

    fun loadTabOrder(): List<String> {
        val raw = prefs.getString(KEY_TAB_ORDER, null) ?: return defaultTabOrder()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }.getOrElse { defaultTabOrder() }
    }

    fun saveTabOrder(order: List<String>) {
        val allowed = setOf("NS", "PSP", "GBA", "ANDROID")
        val clean = (order.filter { it in allowed } + defaultTabOrder()).distinct()
        val arr = JSONArray()
        clean.forEach { arr.put(it) }
        prefs.edit().putString(KEY_TAB_ORDER, arr.toString()).apply()
    }

    fun loadScraperSettings(): ScraperSettings = ScraperSettings(
        useLibretro = prefs.getBoolean(KEY_SCRAPER_LIBRETRO, true),
        theGamesDbApiKey = prefs.getString(KEY_SCRAPER_TGDB, "").orEmpty(),
        steamGridDbApiKey = prefs.getString(KEY_SCRAPER_STEAMGRID, "").orEmpty(),
        screenScraperUser = prefs.getString(KEY_SCRAPER_SCREEN_USER, "").orEmpty(),
        screenScraperPassword = prefs.getString(KEY_SCRAPER_SCREEN_PASS, "").orEmpty()
    )

    fun saveScraperSettings(settings: ScraperSettings) {
        prefs.edit()
            .putBoolean(KEY_SCRAPER_LIBRETRO, settings.useLibretro)
            .putString(KEY_SCRAPER_TGDB, settings.theGamesDbApiKey.trim())
            .putString(KEY_SCRAPER_STEAMGRID, settings.steamGridDbApiKey.trim())
            .putString(KEY_SCRAPER_SCREEN_USER, settings.screenScraperUser.trim())
            .putString(KEY_SCRAPER_SCREEN_PASS, settings.screenScraperPassword.trim())
            .apply()
    }

    private fun defaultPlatforms(): List<PlatformConfig> = listOf(
        PlatformConfig(id = PlatformKind.PSP.name, kind = PlatformKind.PSP),
        PlatformConfig(id = PlatformKind.SWITCH.name, kind = PlatformKind.SWITCH),
        PlatformConfig(id = PlatformKind.GBA.name, kind = PlatformKind.GBA)
    )

    private fun mergeDefaultPlatforms(platforms: List<PlatformConfig>): List<PlatformConfig> {
        val existingIds = platforms.map { it.id }.toSet()
        return platforms + defaultPlatforms().filter { it.id !in existingIds }
    }

    private fun defaultTabOrder(): List<String> = listOf("PSP", "NS", "GBA", "ANDROID")

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
        return this
    }

    companion object {
        private const val KEY_PLATFORMS = "platforms"
        private const val KEY_GAMES = "games"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENT = "recent"
        private const val KEY_ITEM_OVERRIDES = "item_overrides"
        private const val KEY_HOME_PROMPT_DONE = "home_prompt_done"
        private const val KEY_ANDROID_GAMES = "android_games"
        private const val KEY_LANDSCAPE_MODE = "landscape_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_SAFE_MARGIN_LEFT = "safe_margin_left"
        private const val KEY_SAFE_MARGIN_RIGHT = "safe_margin_right"
        private const val KEY_TAB_ORDER = "tab_order"
        private const val KEY_SCRAPER_LIBRETRO = "scraper_libretro"
        private const val KEY_SCRAPER_TGDB = "scraper_tgdb_key"
        private const val KEY_SCRAPER_STEAMGRID = "scraper_steamgrid_key"
        private const val KEY_SCRAPER_SCREEN_USER = "scraper_screenscraper_user"
        private const val KEY_SCRAPER_SCREEN_PASS = "scraper_screenscraper_pass"
    }
}
