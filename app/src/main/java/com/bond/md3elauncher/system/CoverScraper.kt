package com.bond.md3elauncher.system

import android.content.Context
import android.net.Uri
import com.bond.md3elauncher.data.CoverCandidate
import com.bond.md3elauncher.data.ScraperSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class CoverScraper(private val context: Context) {
    var lastReport: String = ""
        private set

    fun searchCovers(
        title: String,
        platformLabel: String,
        serial: String?,
        settings: ScraperSettings
    ): List<CoverCandidate> {
        val cleanTitle = normalizeTitle(title)
        if (cleanTitle.isBlank()) {
            lastReport = "搜索标题为空"
            return emptyList()
        }

        val queries = buildSearchQueries(cleanTitle, serial)
        val result = linkedMapOf<String, CoverCandidate>()
        val report = mutableListOf<String>()
        report.add("标题：$cleanTitle")
        report.add("关键词：${queries.joinToString(" / ")}")

        if (settings.useLibretro) {
            val libretro = searchLibretro(queries, platformLabel)
            report.add("Libretro：${libretro.size} 张")
            libretro.forEach { result[it.imageUrl] = it }
        } else {
            report.add("Libretro：已关闭")
        }

        if (settings.steamGridDbApiKey.isNotBlank()) {
            val steam = searchSteamGridDb(queries, settings.steamGridDbApiKey)
            report.add("SteamGridDB：${steam.size} 张")
            steam.forEach { result[it.imageUrl] = it }
        } else {
            report.add("SteamGridDB：未填写 Key")
        }

        if (settings.theGamesDbApiKey.isNotBlank()) {
            val tgdb = searchTheGamesDb(queries, platformLabel, settings.theGamesDbApiKey)
            report.add("TheGamesDB：${tgdb.size} 张")
            tgdb.forEach { result[it.imageUrl] = it }
        } else {
            report.add("TheGamesDB：未填写 Key")
        }

        lastReport = report.joinToString("\n")
        return result.values.take(36)
    }

    fun downloadCandidate(candidate: CoverCandidate, key: String): String? {
        return runCatching {
            val conn = openConnection(candidate.imageUrl, method = "GET")
            if (conn.responseCode !in 200..299) return@runCatching null
            val dir = File(context.cacheDir, "scraped_covers")
            if (!dir.exists()) dir.mkdirs()
            val safeName = key.map { ch ->
                if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.') ch else '_'
            }.joinToString("").take(120)
            val outFile = File(dir, "${safeName}_${System.currentTimeMillis()}.img")
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile.absolutePath
        }.getOrNull()
    }

    private fun searchLibretro(queries: List<String>, platformLabel: String): List<CoverCandidate> {
        val repo = when (platformLabel.lowercase(Locale.ROOT)) {
            "psp" -> "Sony_-_PlayStation_Portable"
            "ns", "switch" -> "Nintendo_-_Nintendo_Switch"
            "gba" -> "Nintendo_-_Game_Boy_Advance"
            else -> return emptyList()
        }
        val out = linkedMapOf<String, CoverCandidate>()
        queries.flatMap { buildTitleVariants(it) }.forEach { variant ->
            val encoded = pathEncode("$variant.png")
            val url = "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/Named_Boxarts/$encoded"
            if (urlExists(url)) out[url] = CoverCandidate(variant, url, "Libretro")
        }
        return out.values.toList()
    }

    private fun searchSteamGridDb(queries: List<String>, apiKey: String): List<CoverCandidate> {
        val candidates = linkedMapOf<String, CoverCandidate>()
        queries.take(6).forEach { query ->
            runCatching {
                val searchUrl = "https://www.steamgriddb.com/api/v2/search/autocomplete/${pathEncode(query)}"
                val searchJson = getJson(searchUrl, mapOf("Authorization" to "Bearer $apiKey"))
                val games = searchJson.optJSONArray("data") ?: return@runCatching
                for (i in 0 until minOf(games.length(), 5)) {
                    val game = games.optJSONObject(i) ?: continue
                    val id = game.optLong("id", 0L)
                    val gameName = game.optString("name", query)
                    if (id <= 0L) continue
                    val gridUrl = "https://www.steamgriddb.com/api/v2/grids/game/$id?types=static&styles=alternate,material,white_logo,blurred,default&dimensions=600x900,342x482,660x930,512x1024"
                    val gridJson = getJson(gridUrl, mapOf("Authorization" to "Bearer $apiKey"))
                    val grids = gridJson.optJSONArray("data") ?: continue
                    for (j in 0 until minOf(grids.length(), 10)) {
                        val grid = grids.optJSONObject(j) ?: continue
                        val imageUrl = grid.optString("url").takeIf { it.startsWith("http") } ?: continue
                        candidates[imageUrl] = CoverCandidate(gameName, imageUrl, "SteamGridDB")
                    }
                }
            }
        }
        return candidates.values.toList()
    }

    private fun searchTheGamesDb(queries: List<String>, platformLabel: String, apiKey: String): List<CoverCandidate> {
        val out = linkedMapOf<String, CoverCandidate>()
        val platformFilter = theGamesDbPlatformFilter(platformLabel)
        val gameNames = mutableMapOf<String, String>()
        val gameIds = linkedSetOf<String>()

        queries.take(8).forEach { query ->
            val idsFromV11 = runCatching {
                searchTgdbIdsByName(query, apiKey, platformFilter, gameNames, useV11 = true)
            }.getOrDefault(emptyList())
            gameIds.addAll(idsFromV11)

            if (idsFromV11.isEmpty()) {
                val idsFromV1 = runCatching {
                    searchTgdbIdsByName(query, apiKey, platformFilter, gameNames, useV11 = false)
                }.getOrDefault(emptyList())
                gameIds.addAll(idsFromV1)
            }

            if (gameIds.size >= 8) return@forEach
        }

        if (gameIds.isEmpty()) return emptyList()

        gameIds.chunked(8).forEach { chunk ->
            runCatching {
                val ids = chunk.joinToString(",")
                val url = "https://api.thegamesdb.net/v1/Games/Images?apikey=${formEncode(apiKey)}&games_id=${formEncode(ids)}&filter%5Btype%5D=boxart"
                val json = getJson(url)
                parseTgdbImages(json, gameNames).forEach { candidate -> out[candidate.imageUrl] = candidate }
            }
        }

        return out.values.toList()
    }

    private fun searchTgdbIdsByName(
        query: String,
        apiKey: String,
        platformFilter: String?,
        gameNames: MutableMap<String, String>,
        useV11: Boolean
    ): List<String> {
        val endpoint = if (useV11) "v1.1" else "v1"
        val platformPart = platformFilter?.let { "&filter%5Bplatform%5D=${formEncode(it)}" }.orEmpty()
        val url = "https://api.thegamesdb.net/$endpoint/Games/ByGameName?apikey=${formEncode(apiKey)}&name=${formEncode(query)}&fields=platform,alternates&include=platform$platformPart"
        val json = getJson(url)
        val data = json.optJSONObject("data") ?: return emptyList()
        val games = data.optJSONArray("games") ?: return emptyList()
        val ids = mutableListOf<String>()
        for (i in 0 until minOf(games.length(), 12)) {
            val game = games.optJSONObject(i) ?: continue
            val id = game.optString("id").takeIf { it.isNotBlank() } ?: continue
            ids.add(id)
            gameNames[id] = game.optString("game_title").ifBlank { query }
        }
        return ids
    }

    private fun parseTgdbImages(json: JSONObject, gameNames: Map<String, String>): List<CoverCandidate> {
        val data = json.optJSONObject("data") ?: return emptyList()
        val baseUrls = data.optJSONObject("base_url")
        val baseOriginal = baseUrls?.optString("original").orEmpty().trimEnd('/')
        val images = data.optJSONObject("images") ?: data.optJSONObject("boxart") ?: return emptyList()
        val out = mutableListOf<CoverCandidate>()
        val ids = images.keys()
        while (ids.hasNext()) {
            val id = ids.next()
            val arr = images.optJSONArray(id) ?: continue
            for (i in 0 until minOf(arr.length(), 8)) {
                val item = arr.optJSONObject(i) ?: continue
                val side = item.optString("side")
                val type = item.optString("type")
                if (side.isNotBlank() && side.lowercase(Locale.ROOT) != "front") continue
                if (type.isNotBlank() && type.lowercase(Locale.ROOT) != "boxart") continue
                val fileName = item.optString("filename").takeIf { it.isNotBlank() } ?: continue
                val title = gameNames[id] ?: item.optString("name").ifBlank { "TheGamesDB" }
                buildTgdbImageUrls(baseOriginal, fileName).forEach { url ->
                    out.add(CoverCandidate(title, url, "TheGamesDB"))
                }
            }
        }
        return out
    }

    private fun buildTgdbImageUrls(baseOriginal: String, fileName: String): List<String> {
        if (fileName.startsWith("http")) return listOf(fileName)
        val clean = fileName.trimStart('/')
        val candidates = linkedSetOf<String>()
        if (baseOriginal.isNotBlank()) candidates.add("$baseOriginal/$clean")
        candidates.add("https://cdn.thegamesdb.net/images/original/$clean")
        candidates.add("https://cdn.thegamesdb.net/images/$clean")
        // Some responses already include paths such as boxart/original/front/xxx.jpg.
        if (clean.contains("/original/")) candidates.add("https://cdn.thegamesdb.net/images/$clean")
        return candidates.toList()
    }

    private fun theGamesDbPlatformFilter(platformLabel: String): String? {
        // These values can change if TGDB changes their platform table. We keep the filter optional:
        // if a platform-filtered search fails, aliases still run through unfiltered query variants.
        return when (platformLabel.lowercase(Locale.ROOT)) {
            "psp" -> "38" // Sony Playstation Portable on TGDB in most public lists.
            // Switch ID is less consistently documented in public mirrors, so avoid a hard filter here.
            "ns", "switch" -> null
            else -> null
        }
    }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val conn = openConnection(url, method = "GET", headers = headers)
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(180)}")
        return JSONObject(body)
    }

    private fun urlExists(url: String): Boolean {
        return runCatching {
            val conn = openConnection(url, method = "HEAD")
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_BAD_METHOD || code == HttpURLConnection.HTTP_FORBIDDEN) {
                val get = openConnection(url, method = "GET")
                get.setRequestProperty("Range", "bytes=0-0")
                get.responseCode in 200..299
            } else {
                code in 200..299
            }
        }.getOrDefault(false)
    }

    private fun openConnection(url: String, method: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 9000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "GameHub/0.1.87 Android")
        conn.setRequestProperty("Accept", "application/json,image/*,*/*")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn
    }

    private fun normalizeTitle(value: String): String = Uri.decode(value)
        .replace("™", "")
        .replace("®", "")
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun buildSearchQueries(title: String, serial: String?): List<String> {
        val base = normalizeTitle(title)
        val out = linkedSetOf<String>()
        fun add(value: String?) {
            val v = value?.let { normalizeTitle(it) }.orEmpty()
            if (v.length >= 2) out.add(v)
        }

        add(base)
        add(serial)
        add(base.replace("：", ":"))
        add(base.replace(Regex("[\\[\\(（【].*?[\\]\\)）】]"), " "))
        add(base.substringBefore(":").substringBefore("："))
        add(base.substringBefore(" - "))

        val lower = base.lowercase(Locale.ROOT)
        if (base.contains("星之卡比") || lower.contains("hoshi no kirby") || lower.contains("kirby")) {
            add("Kirby and the Forgotten Land")
            add("Hoshi no Kirby Discovery")
            add("Kirby Discovery")
            add("Kirby")
            add("星之卡比")
        }
        if (base.contains("但丁") || lower.contains("dante")) {
            add("Dante's Inferno")
            add("Dantes Inferno")
        }
        if (base.contains("死亡细胞") || lower.contains("dead cells")) add("Dead Cells")

        return out.filter { it.isNotBlank() }.take(10)
    }

    private fun buildTitleVariants(title: String): List<String> {
        val base = title.trim()
        return listOf(
            base,
            "$base (USA)",
            "$base (Europe)",
            "$base (Japan)",
            base.replace(":", " -")
        ).distinct().filter { it.isNotBlank() }
    }

    private fun pathEncode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun formEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
