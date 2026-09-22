package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.CoverCandidate
import org.json.JSONObject
import java.util.Locale

internal object CoverSources {
    fun libretroRepos(platform: String): List<String> = when (platform.lowercase(Locale.ROOT)) {
        "psp" -> listOf("Sony_-_PlayStation_Portable")
        "gba" -> listOf("Nintendo_-_Game_Boy_Advance")
        "gb", "gbc", "gb/gbc" -> listOf("Nintendo_-_Game_Boy", "Nintendo_-_Game_Boy_Color")
        "nes", "fc/nes" -> listOf("Nintendo_-_Nintendo_Entertainment_System")
        "sfc", "snes", "sfc/snes" -> listOf("Nintendo_-_Super_Nintendo_Entertainment_System")
        "md", "genesis", "md/genesis" -> listOf("Sega_-_Mega_Drive_-_Genesis")
        "ps1" -> listOf("Sony_-_PlayStation")
        "n64" -> listOf("Nintendo_-_Nintendo_64")
        "arcade" -> listOf("MAME")
        else -> emptyList()
    }

    fun screenScraper(json: JSONObject, fallback: String): List<CoverCandidate> {
        val games = json.optJSONObject("response")?.optJSONArray("jeux") ?: return emptyList()
        val out = linkedMapOf<String, CoverCandidate>()
        for (i in 0 until minOf(games.length(), 8)) {
            val game = games.optJSONObject(i) ?: continue
            val title = game.optJSONArray("noms")?.optJSONObject(0)?.optString("text").orEmpty().ifBlank { fallback }
            val medias = game.optJSONArray("medias") ?: continue
            for (j in 0 until medias.length()) {
                val media = medias.optJSONObject(j) ?: continue
                if (media.optString("type") !in setOf("box-2D", "box-3D", "steamgrid")) continue
                val url = media.optString("url")
                if (url.startsWith("https://")) out[url] = CoverCandidate(title, url, "ScreenScraper")
            }
        }
        return out.values.take(24)
    }
}
