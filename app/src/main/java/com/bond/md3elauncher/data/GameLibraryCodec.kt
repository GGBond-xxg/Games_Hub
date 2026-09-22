package com.bond.md3elauncher.data

import org.json.JSONArray
import org.json.JSONObject

internal data class DecodedLibrary(val games: List<GameItem>, val rejected: Int)

internal object GameLibraryCodec {
    fun decode(raw: String): DecodedLibrary? = runCatching {
        val array = JSONArray(raw)
        var rejected = 0
        val ids = mutableSetOf<String>()
        val games = buildList {
            for (i in 0 until array.length()) {
                val game = runCatching {
                    val obj = array.getJSONObject(i)
                    fun text(key: String): String = (obj.get(key) as? String)?.takeIf { it.isNotBlank() }
                        ?: error("Invalid game field")
                    fun optional(key: String) = (obj.opt(key) as? String)?.takeIf { it.isNotBlank() }
                    GameItem(text("id"), text("platformId"), text("platformTitle"), text("title"),
                        text("fileName"), text("extension"), text("uri"), obj.optLong("addedAt", 0L),
                        optional("serial"), optional("coverPath"), optional("backgroundPath"))
                }.getOrNull()
                if (game != null && ids.add(game.id)) add(game) else rejected++
            }
        }
        DecodedLibrary(games, rejected)
    }.getOrNull()

    fun encode(games: List<GameItem>): String = JSONArray().apply {
        games.forEach { g -> put(JSONObject().put("id", g.id).put("platformId", g.platformId)
            .put("platformTitle", g.platformTitle).put("title", g.title).put("fileName", g.fileName)
            .put("extension", g.extension).put("uri", g.uri).put("addedAt", g.addedAt)
            .put("serial", g.serial ?: JSONObject.NULL).put("coverPath", g.coverPath ?: JSONObject.NULL)
            .put("backgroundPath", g.backgroundPath ?: JSONObject.NULL)) }
    }.toString()

    fun recover(current: String?, previous: String?): DecodedLibrary {
        if (current == null) return DecodedLibrary(emptyList(), 0)
        val decoded = decode(current)
        val backup = previous?.let(::decode)?.takeIf { it.rejected == 0 }
        return when {
            decoded == null -> DecodedLibrary(backup?.games.orEmpty(), 1)
            decoded.games.isEmpty() && decoded.rejected > 0 -> DecodedLibrary(backup?.games.orEmpty(), decoded.rejected)
            else -> decoded
        }
    }
}
