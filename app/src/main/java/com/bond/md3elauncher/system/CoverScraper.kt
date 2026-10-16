package com.bond.md3elauncher.system

import android.content.Context
import android.net.Uri
import com.bond.md3elauncher.BuildConfig
import com.bond.md3elauncher.data.CoverCandidate
import com.bond.md3elauncher.data.ScraperSettings
import com.bond.md3elauncher.i18n.I18n
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CancellationException

enum class CoverArtworkType {
    BOX_ART,
    SCREENSHOT
}

class CoverScraper(private val context: Context) {
    var lastReport: String = ""
        private set
    var lastErrorKey: String? = null
        private set

    fun searchCovers(
        title: String,
        platformLabel: String,
        serial: String?,
        settings: ScraperSettings,
        artworkType: CoverArtworkType = CoverArtworkType.BOX_ART
    ): List<CoverCandidate> {
        lastErrorKey = null
        val cleanTitle = normalizeTitle(title)
        if (cleanTitle.isBlank()) {
            lastReport = I18n.t(context, "cover.report.empty_title", "Search title is empty")
            return emptyList()
        }

        val queries = buildSearchQueries(cleanTitle, serial)
        val result = linkedMapOf<String, CoverCandidate>()
        val report = mutableListOf<String>()
        report.add(I18n.t(context, "cover.report.title", "Title: {title}", "title" to cleanTitle))
        report.add(I18n.t(context, "cover.report.queries", "Queries: {queries}", "queries" to queries.joinToString(" / ")))

        if (settings.useLibretro) {
            val libretro = searchLibretro(queries, platformLabel, artworkType)
            report.add(I18n.t(context, "cover.report.libretro_count", "Libretro: {count}", "count" to libretro.size))
            libretro.forEach { result[it.imageUrl] = it }
        } else {
            report.add(I18n.t(context, "cover.report.libretro_disabled", "Libretro: disabled"))
        }

        if (settings.steamGridDbApiKey.isNotBlank()) {
            val steam = searchSteamGridDb(queries, settings.steamGridDbApiKey)
            report.add(I18n.t(context, "cover.report.steamgrid_count", "SteamGridDB: {count}", "count" to steam.size))
            steam.forEach { result[it.imageUrl] = it }
        } else {
            report.add(I18n.t(context, "cover.report.steamgrid_no_key", "SteamGridDB: API key not set"))
        }

        if (settings.theGamesDbApiKey.isNotBlank()) {
            try {
                val tgdb = TheGamesDb().searchArtwork(queries, platformLabel, settings.theGamesDbApiKey, artworkType)
                report.add(I18n.t(context, "cover.report.tgdb_count", "TheGamesDB: {count}", "count" to tgdb.size))
                tgdb.forEach { result[it.imageUrl] = it }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (interrupted: InterruptedException) { throw interrupted
            } catch (failure: ScrapeHttpFailure) {
                val key = when (failure.status) {
                    401, 403 -> "cover.report.tgdb_auth"
                    429 -> "cover.report.tgdb_quota"
                    else -> "cover.report.network_failed"
                }
                lastErrorKey = key
                report.add(I18n.t(context, key, "TheGamesDB request failed"))
            } catch (_: Exception) {
                lastErrorKey = "cover.report.network_failed"
                report.add(I18n.t(context, "cover.report.network_failed", "Request failed. Check your network and try again."))
            }
        } else {
            report.add(I18n.t(context, "cover.report.tgdb_no_key", "TheGamesDB: API key not set"))
        }

        if (settings.screenScraperDevId.isNotBlank() && settings.screenScraperDevPassword.isNotBlank()) {
            try {
                val parameters = linkedMapOf("devid" to settings.screenScraperDevId, "devpassword" to settings.screenScraperDevPassword,
                    "softname" to "GameHub", "output" to "json", "recherche" to cleanTitle)
                if (settings.screenScraperUser.isNotBlank()) {
                    parameters["ssid"] = settings.screenScraperUser
                    parameters["sspassword"] = settings.screenScraperPassword
                }
                val url = "https://api.screenscraper.fr/api2/jeuRecherche.php?" + parameters.entries.joinToString("&") { "${it.key}=${formEncode(it.value)}" }
                val covers = CoverSources.screenScraper(getJson(url), cleanTitle, artworkType)
                covers.forEach { result[it.imageUrl] = it }
                report.add(I18n.t(context, "cover.report.screenscraper_count", "ScreenScraper: {count}", "count" to covers.size))
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (interrupted: InterruptedException) { throw interrupted
            } catch (_: Exception) {
                report.add(I18n.t(context, "cover.report.screenscraper_failed", "ScreenScraper request failed. Check credentials, quota and network."))
            }
        } else {
            report.add(I18n.t(context, "cover.report.screenscraper_no_key", "ScreenScraper: developer credentials not set"))
        }
        lastReport = report.joinToString("\n")
        return result.values.take(36)
    }

    fun downloadCandidate(candidate: CoverCandidate, key: String): String? = cancellableAttempt {
        val bytes = ScrapeNetwork.request {
            val conn = openConnection(candidate.imageUrl, method = "GET")
            try {
                check(conn.responseCode in 200..299)
                require(conn.contentLengthLong <= 8 * 1024 * 1024)
                conn.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 8 * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            } finally { conn.disconnect() }
        }
        val dir = File(context.cacheDir, "scraped_covers").apply { mkdirs() }
        val safeName = key.map { if (it.isLetterOrDigit() || it in "_-.") it else '_' }.joinToString("").take(120)
        val outFile = File(dir, "${safeName}_${System.currentTimeMillis()}.img")
        try { outFile.writeBytes(bytes); outFile.absolutePath }
        catch (error: Exception) { outFile.delete(); throw error }
    }.getOrNull()

    private inline fun <T> cancellableAttempt(block: () -> T): Result<T> = runCatching(block).onFailure {
        if (it is CancellationException || it is InterruptedException) throw it
    }

    private fun searchLibretro(queries: List<String>, platformLabel: String, artworkType: CoverArtworkType): List<CoverCandidate> {
        val repos = CoverSources.libretroRepos(platformLabel)
        val out = linkedMapOf<String, CoverCandidate>()
        val deadline = System.nanoTime() + 25_000_000_000L
        for (variant in queries.flatMap { buildTitleVariants(it) }.distinct().take(24)) {
            for (repo in repos) {
                if (System.nanoTime() >= deadline) return out.values.toList()
                val url = CoverSources.libretroImageUrl(repo, variant, artworkType)
                if (urlExists(url)) out[url] = CoverCandidate(variant, url, "Libretro")
            }
        }
        return out.values.toList()
    }

    private fun searchSteamGridDb(queries: List<String>, apiKey: String): List<CoverCandidate> {
        val candidates = linkedMapOf<String, CoverCandidate>()
        queries.take(6).forEach { query ->
            cancellableAttempt {
                val searchUrl = "https://www.steamgriddb.com/api/v2/search/autocomplete/${pathEncode(query)}"
                val searchJson = getJson(searchUrl, mapOf("Authorization" to "Bearer $apiKey"))
                val games = searchJson.optJSONArray("data") ?: return@cancellableAttempt
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

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject = ScrapeNetwork.request {
        val conn = openConnection(url, method = "GET", headers = headers)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    require(text.length + count <= 4 * 1024 * 1024)
                    text.append(buffer, 0, count)
                }
                text.toString()
            }
            JSONObject(body)
        } finally { conn.disconnect() }
    }

    private fun urlExists(url: String): Boolean = cancellableAttempt { ScrapeNetwork.request(7000) {
        val conn = openConnection(url, method = "HEAD")
        try { conn.responseCode in 200..299 } finally { conn.disconnect() }
    } }.getOrDefault(false)

    private fun openConnection(url: String, method: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 9000
        conn.readTimeout = 15000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "GameHub/${BuildConfig.VERSION_NAME} Android")
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

        GameTitleQueries.manual(base).forEach(::add)

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
