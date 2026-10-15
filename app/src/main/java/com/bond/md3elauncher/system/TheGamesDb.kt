package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.CoverCandidate
import com.bond.md3elauncher.data.GameMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Shared manual/batch API adapter. The injected reader must bound responses and redact URLs on failure. */
internal class TheGamesDb(private val read: (String) -> JSONObject = ::readGameDatabaseJson) {
    private val dictionaries = mutableMapOf<String, Map<String, String>>()
    fun searchArtwork(queries: List<String>, platformLabel: String, apiKey: String, type: CoverArtworkType): List<CoverCandidate> {
        val games = linkedMapOf<String, JSONObject>()
        for (query in queries.take(5)) {
            search(query, apiKey, platform(platformLabel)).take(12).forEach { games[it.optString("id")] = it }
            if (games.size >= 12) break
        }
        return images(games.values.take(12), apiKey, type)
    }
    fun search(query: String, apiKey: String, platform: String?): List<JSONObject> {
        val params = linkedMapOf("apikey" to apiKey.trim(), "name" to query,
            "fields" to "genres,publishers,alternates")
        platform?.let { params["filter[platform]"] = it }
        return read(url("v1.1/Games/ByGameName", params)).optJSONObject("data")?.optJSONArray("games").objects()
    }
    fun images(games: List<JSONObject>, apiKey: String, type: CoverArtworkType): List<CoverCandidate> {
        if (games.isEmpty()) return emptyList()
        val names = games.associate { it.optString("id") to it.optString("game_title") }
        val json = read(url("v1/Games/Images", mapOf("apikey" to apiKey.trim(), "games_id" to names.keys.joinToString(","),
            "filter[type]" to if (type == CoverArtworkType.BOX_ART) "boxart" else "screenshot")))
        return parseImages(json, names, type)
    }
    fun metadata(game: JSONObject, apiKey: String): GameMetadata {
        fun names(kind: String): String {
            val ids = game.optJSONArray(kind) ?: return ""
            if (ids.length() == 0) return ""
            val dictionary = dictionaries.getOrPut(kind) {
                val data = read(url("v1/" + kind.replaceFirstChar { it.uppercase() }, mapOf("apikey" to apiKey.trim())))
                    .optJSONObject("data")?.optJSONObject(kind)
                data?.keys()?.asSequence()?.associateWith { data.optJSONObject(it)?.optString("name").orEmpty() }.orEmpty()
            }
            return (0 until ids.length()).mapNotNull { dictionary[ids.optString(it)] }.filter { it.isNotBlank() }.distinct().joinToString(" / ")
        }
        return GameMetadata(game.optString("release_date").takeUnless { it == "null" }.orEmpty(),
            names("genres"), names("developers"), names("publishers")).cleaned()
    }
    companion object {
        private fun url(path: String, params: Map<String, String>) = "https://api.thegamesdb.net/$path?" +
            params.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }

        // Verified against the official /v1/Platforms catalogue (2026-10-16).
        fun platform(label: String): String? = when (label.lowercase(Locale.ROOT)) {
            "psp" -> "13"; "ns", "switch" -> "4971"; "gba" -> "5"
            "gb" -> "4"; "gbc" -> "41"; "gb/gbc" -> "4,41"
            "nes", "fc", "fc/nes" -> "7"; "sfc", "snes", "sfc/snes" -> "6"
            "md", "genesis", "md/genesis" -> "18"; "ps1" -> "10"; "ps2" -> "11"
            "n64" -> "3"; "arcade" -> "23"; "nds", "nintendo ds" -> "8"
            "n3ds", "3ds", "nintendo 3ds" -> "4912"; "gc", "gamecube" -> "2"
            "wii" -> "9"; "dc", "dreamcast" -> "16"
            else -> null // Manual artwork search may intentionally cross platforms for Android apps.
        }
        fun parseImages(json: JSONObject, names: Map<String, String>, type: CoverArtworkType): List<CoverCandidate> {
            val data = json.optJSONObject("data") ?: return emptyList()
            val base = data.optJSONObject("base_url")?.optString("original").orEmpty().trimEnd('/')
            if (!base.startsWith("https://")) return emptyList()
            val images = data.optJSONObject("images") ?: return emptyList()
            val wanted = if (type == CoverArtworkType.BOX_ART) "boxart" else "screenshot"
            return names.flatMap { (id, title) -> images.optJSONArray(id).objects().filter {
                it.optString("type") == wanted && (type != CoverArtworkType.BOX_ART || it.optString("side") == "front")
            }.mapNotNull {
                val file = it.optString("filename").takeUnless { name -> name.isBlank() || name == "null" || name.contains("..") } ?: return@mapNotNull null
                val imageUrl = if (file.startsWith("https://")) file else "$base/${file.trimStart('/')}"
                CoverCandidate(title, imageUrl, "TheGamesDB")
            } }.distinctBy { it.imageUrl }.take(36)
        }
        /** Same title/platform regional releases are safe; broad or ambiguous aliases are never auto-selected. */
        fun exactGame(games: List<JSONObject>, titles: List<String>, platform: String): JSONObject? {
            val wanted = titles.map(GameScrapeSources::normalizedTitle).filter { it.isNotBlank() }.toSet()
            val matches = games.filter { game -> game.optString("platform") == platform &&
                (listOf(game.optString("game_title")) + game.optJSONArray("alternates").strings()).any { GameScrapeSources.normalizedTitle(it) in wanted }
            }.distinctBy { it.optString("id") }
            if (matches.map { GameScrapeSources.normalizedTitle(it.optString("game_title")) }.distinct().size != 1) return null
            return matches.sortedWith(compareBy<JSONObject> { when (it.optInt("region_id")) { 1 -> 0; 2 -> 1; 6 -> 2; 4 -> 3; else -> 4 } }
                .thenBy { it.optLong("id") }).firstOrNull()
        }
        private fun JSONArray?.objects() = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
        private fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it) as? String }
    }
}

private fun readGameDatabaseJson(url: String): JSONObject {
    repeat(2) { attempt ->
        try { return readGameDatabaseJsonOnce(url) }
        catch (failure: java.io.IOException) {
            if (Thread.currentThread().isInterrupted || attempt == 1) throw failure
            Thread.sleep(300)
        }
    }
    error("Request failed")
}

private fun readGameDatabaseJsonOnce(url: String): JSONObject = ScrapeNetwork.request {
    val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    try {
        connection.connectTimeout = 7000
        connection.readTimeout = 9000
        connection.setRequestProperty("User-Agent", "GameHub/${com.bond.md3elauncher.BuildConfig.VERSION_NAME}")
        val status = connection.responseCode
        if (status !in 200..299) throw ScrapeHttpFailure(status)
        val body = connection.inputStream.bufferedReader().use { input ->
            val output = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = input.read(buffer)
                if (count < 0) break
                require(output.length + count <= 4 * 1024 * 1024)
                output.append(buffer, 0, count)
            }
            output.toString()
        }
        JSONObject(body).also { if (it.optInt("code", 200) !in 200..299) throw ScrapeHttpFailure(it.optInt("code")) }
    } finally { connection.disconnect() }
}

internal object GameTitleQueries {
    // These are exact titles. A series name such as 星之卡比 must not auto-match a specific sequel.
    fun exactAliases(title: String): List<String> = when (GameScrapeSources.normalizedTitle(title)) {
        "星之卡比探索发现", "星之卡比探索發現" -> listOf("Kirby and the Forgotten Land", "Hoshi no Kirby Discovery")
        "星之卡比镜之大迷宫", "星之卡比鏡之大迷宮" -> listOf("Kirby & the Amazing Mirror")
        "星之卡比梦之泉豪华版", "星之卡比夢之泉豪華版" -> listOf("Kirby: Nightmare in Dream Land")
        "高级战争", "高級戰爭" -> listOf("Advance Wars")
        "死亡细胞", "死亡細胞" -> listOf("Dead Cells")
        else -> emptyList()
    }
    fun manual(title: String) = (listOf(title) + exactAliases(title) +
        if (title == "星之卡比") listOf("Kirby") else emptyList()).distinct()
}
