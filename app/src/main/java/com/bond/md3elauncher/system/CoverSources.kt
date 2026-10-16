package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.CoverCandidate
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

internal object CoverSources {
    fun libretroImageUrl(repo: String, title: String, artworkType: CoverArtworkType): String {
        val directory = when (artworkType) {
            CoverArtworkType.BOX_ART -> "Named_Boxarts"
            CoverArtworkType.SCREENSHOT -> "Named_Snaps"
        }
        val fileName = URLEncoder.encode("$title.png", "UTF-8").replace("+", "%20")
        return "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/$directory/$fileName"
    }

    fun libretroRepos(platform: String): List<String> = when (platform.lowercase(Locale.ROOT)) {
        "psp" -> listOf("Sony_-_PlayStation_Portable")
        "gba" -> listOf("Nintendo_-_Game_Boy_Advance")
        "gb", "gbc", "gb/gbc" -> listOf("Nintendo_-_Game_Boy", "Nintendo_-_Game_Boy_Color")
        "nes", "fc/nes" -> listOf("Nintendo_-_Nintendo_Entertainment_System")
        "sfc", "snes", "sfc/snes" -> listOf("Nintendo_-_Super_Nintendo_Entertainment_System")
        "md", "genesis", "md/genesis" -> listOf("Sega_-_Mega_Drive_-_Genesis")
        "sms", "master system" -> listOf("Sega_-_Master_System_-_Mark_III")
        "gg", "game gear" -> listOf("Sega_-_Game_Gear")
        "sg1000", "sg-1000" -> listOf("Sega_-_SG-1000")
        "ps1" -> listOf("Sony_-_PlayStation")
        "ps2" -> listOf("Sony_-_PlayStation_2")
        "nds", "nintendo ds" -> listOf("Nintendo_-_Nintendo_DS", "Nintendo_-_Nintendo_DSi")
        "3ds", "nintendo 3ds" -> listOf("Nintendo_-_Nintendo_3DS")
        "gc", "gamecube" -> listOf("Nintendo_-_GameCube")
        "wii" -> listOf("Nintendo_-_Wii")
        "dc", "dreamcast" -> listOf("Sega_-_Dreamcast")
        "n64" -> listOf("Nintendo_-_Nintendo_64")
        "arcade" -> listOf("MAME")
        else -> emptyList()
    }

    fun screenScraper(json: JSONObject, fallback: String, artworkType: CoverArtworkType = CoverArtworkType.BOX_ART): List<CoverCandidate> {
        val games = json.optJSONObject("response")?.optJSONArray("jeux") ?: return emptyList()
        val out = linkedMapOf<String, CoverCandidate>()
        for (i in 0 until minOf(games.length(), 8)) {
            val game = games.optJSONObject(i) ?: continue
            val title = game.optJSONArray("noms")?.optJSONObject(0)?.optString("text").orEmpty().ifBlank { fallback }
            val medias = game.optJSONArray("medias") ?: continue
            for (j in 0 until medias.length()) {
                val media = medias.optJSONObject(j) ?: continue
                if (media.optString("type") !in (if (artworkType == CoverArtworkType.SCREENSHOT) setOf("ss") else setOf("box-2D", "box-3D", "steamgrid"))) continue
                val url = media.optString("url")
                if (url.startsWith("https://")) out[url] = CoverCandidate(title, url, "ScreenScraper")
            }
        }
        return out.values.take(24)
    }
}
