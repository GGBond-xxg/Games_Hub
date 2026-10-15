package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class GameScrapeResult(
    val metadata: GameMetadata = GameMetadata(), val coverUrl: String? = null, val screenshotUrl: String? = null
)

internal object GameScrapeSources {
    fun systemId(game: GameItem): Int? = when (game.platformId) {
        "MD" -> 1; "NES" -> 3; "SFC" -> 4; "GB" -> if (game.extension.equals("gbc", true)) 10 else 9
        "GBA" -> 12; "GC" -> 13; "NDS" -> 15; "WII" -> 16; "N3DS" -> 17; "DREAMCAST" -> 23
        "PS1" -> 57; "PS2" -> 58; "PSP" -> 61; "N64" -> 64; "ARCADE" -> 75; "SWITCH" -> 225
        else -> null
    }
    fun normalizedTitle(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)|\\[[^]]*\\]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

    /** Automatic jobs require one exact normalized title AND the correct system. Never choose a fuzzy first result. */
    fun screenScraper(json: JSONObject, title: String, systemId: Int, language: String): GameScrapeResult? {
        val games = json.optJSONObject("response")?.optJSONArray("jeux") ?: return null
        val wanted = normalizedTitle(title)
        if (wanted.isBlank()) return null
        val matches = games.objects().filter { game ->
            game.optJSONObject("systeme")?.optInt("id") == systemId &&
                game.optJSONArray("noms").objects().any { normalizedTitle(it.optString("text")) == wanted }
        }.distinctBy { it.optString("id", it.toString()) }
        val game = matches.singleOrNull() ?: return null
        val regions = listOf("wor", "us", "eu", "jp")
        fun regionRank(obj: JSONObject) = regions.indexOf(obj.optString("region")).let { if (it < 0) 99 else it }
        fun field(name: String): String = when (val raw = game.opt(name)) {
            is JSONObject -> raw.optString("text")
            is String -> raw
            else -> ""
        }
        val genres = game.optJSONArray("genres").objects().flatMap { it.optJSONArray("noms").objects() }
        val selectedGenres = genres.filter { it.optString("langue") == language }.ifEmpty { genres.filter { it.optString("langue") == "en" } }
        val metadata = GameMetadata(
            game.optJSONArray("dates").objects().sortedBy(::regionRank).firstOrNull()?.optString("text").orEmpty(),
            selectedGenres.map { it.optString("text") }.filter { it.isNotBlank() }.distinct().joinToString(" / "),
            field("developpeur"), field("editeur")
        ).cleaned()
        val media = game.optJSONArray("medias").objects().sortedBy(::regionRank)
        fun image(types: Set<String>) = media.firstOrNull { it.optString("type") in types && it.optString("url").startsWith("https://") }?.optString("url")
        return GameScrapeResult(metadata, image(setOf("box-2D")), image(setOf("ss")))
    }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { optJSONObject(it) }
}

internal data class ScrapeNeeds(val cover: Boolean, val screenshot: Boolean, val metadata: Boolean) {
    val any get() = cover || screenshot || metadata
    companion object {
        // A user-selected image or embedded artwork counts as present; never overwrite it automatically.
        fun forGame(game: GameItem, override: ItemOverride?) = ScrapeNeeds(
            override?.gridImagePath.isNullOrBlank() && game.coverPath.isNullOrBlank(),
            override?.previewImagePath.isNullOrBlank() && game.backgroundPath.isNullOrBlank(),
            override?.metadata?.isComplete != true
        )
    }
}

data class BatchScrapeState(
    val platformId: String = "", val running: Boolean = false, val cancelling: Boolean = false,
    val completed: Int = 0, val total: Int = 0, val updated: Int = 0, val unmatched: Int = 0,
    val failed: Int = 0, val currentTitle: String = "", val messageKey: String = "batch.ready"
)
