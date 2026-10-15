package com.bond.md3elauncher.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bond.md3elauncher.BuildConfig
import com.bond.md3elauncher.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

internal class ScrapeHttpFailure(val status: Int) : Exception("HTTP $status")
internal data class ScrapedGame(val metadata: GameMetadata, val cover: String?, val screenshot: String?)

/** Bounded sequential requests. No credentials or response URLs are included in UI/logs. */
internal class GameScraper(private val context: Context) {
    suspend fun scrape(game: GameItem, needs: ScrapeNeeds, settings: ScraperSettings, language: String): ScrapedGame {
        var result = GameScrapeResult()
        val systemId = GameScrapeSources.systemId(game)
        if (settings.screenScraperDevId.isNotBlank() && settings.screenScraperDevPassword.isNotBlank() && systemId != null) {
            val params = linkedMapOf("devid" to settings.screenScraperDevId, "devpassword" to settings.screenScraperDevPassword,
                "softname" to "GameHub", "output" to "json", "systemeid" to systemId.toString(), "recherche" to game.title)
            if (settings.screenScraperUser.isNotBlank()) {
                params["ssid"] = settings.screenScraperUser; params["sspassword"] = settings.screenScraperPassword
            }
            val url = "https://api.screenscraper.fr/api2/jeuRecherche.php?" + params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
            val bytes = request(url, 4 * 1024 * 1024)
            if (bytes != null) result = GameScrapeSources.screenScraper(JSONObject(String(bytes, Charsets.UTF_8)), game.title, systemId, language) ?: result
        }
        var cover: String? = null
        var screenshot: String? = null
        try {
            if (needs.cover) cover = image(result.coverUrl) ?: if (settings.useLibretro) libretro(game, CoverArtworkType.BOX_ART) else null
            if (needs.screenshot) screenshot = image(result.screenshotUrl) ?: if (settings.useLibretro) libretro(game, CoverArtworkType.SCREENSHOT) else null
            currentCoroutineContext().ensureActive()
            return ScrapedGame(result.metadata, cover, screenshot)
        } catch (error: Throwable) {
            cover?.let { File(it).delete() }; screenshot?.let { File(it).delete() }
            throw error
        }
    }
    private suspend fun libretro(game: GameItem, type: CoverArtworkType): String? {
        val title = game.title.trim()
        val variants = listOf(title, "$title (USA)", "$title (Europe)", "$title (Japan)").distinct()
        val repositories = CoverSources.libretroRepos(game.platformTitle).filter {
            if (game.platformId == "GB") it.endsWith(if (game.extension.equals("gbc", true)) "Game_Boy_Color" else "Game_Boy") else true
        }
        for (name in variants) for (repo in repositories) {
            currentCoroutineContext().ensureActive()
            image(CoverSources.libretroImageUrl(repo, name, type))?.let { return it }
            delay(150)
        }
        return null
    }
    private suspend fun image(url: String?): String? {
        if (url == null) return null
        val bytes = request(url, 8 * 1024 * 1024) ?: return null
        var pendingFile: File? = null
        try { return withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > 40_000_000L) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
            val file = File(File(context.filesDir, "custom_icons").apply { mkdirs() }, "scrape_${UUID.randomUUID()}.png")
            pendingFile = file
            try {
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                currentCoroutineContext().ensureActive()
                file.absolutePath
            } catch (error: Throwable) { file.delete(); throw error }
            finally { bitmap.recycle() }
        } } catch (error: Throwable) {
            // Also covers cancellation at the withContext return boundary.
            pendingFile?.delete()
            throw error
        }
    }
    private suspend fun request(url: String, limit: Int): ByteArray? = runInterruptible(Dispatchers.IO) {
        require(url.startsWith("https://"))
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7000; connection.readTimeout = 9000
            connection.setRequestProperty("User-Agent", "GameHub/${BuildConfig.VERSION_NAME}")
            val code = connection.responseCode
            if (code == 404) return@runInterruptible null
            if (code !in 200..299) throw ScrapeHttpFailure(code)
            require(connection.contentLengthLong <= limit)
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= limit)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
    }
}
