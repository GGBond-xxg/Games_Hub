package com.bond.md3elauncher.data

import org.json.JSONObject

/** Stored with artwork overrides so rescanning cannot erase downloaded or edited information. */
data class GameMetadata(
    val releaseDate: String = "",
    val genres: String = "",
    val developer: String = "",
    val publisher: String = ""
) {
    val isEmpty: Boolean get() = listOf(releaseDate, genres, developer, publisher).all { it.isBlank() }
    val isComplete: Boolean get() = listOf(releaseDate, genres, developer, publisher).all { it.isNotBlank() }
    fun fillMissing(other: GameMetadata) = GameMetadata(
        releaseDate.ifBlank { other.releaseDate }, genres.ifBlank { other.genres },
        developer.ifBlank { other.developer }, publisher.ifBlank { other.publisher }
    )
    fun cleaned() = GameMetadata(releaseDate.trim().take(32), genres.trim().take(256), developer.trim().take(256), publisher.trim().take(256))
    fun toJson(): JSONObject = JSONObject().put("releaseDate", releaseDate).put("genres", genres)
        .put("developer", developer).put("publisher", publisher)
    companion object {
        fun fromJson(json: JSONObject?): GameMetadata {
            fun text(key: String) = (json?.opt(key) as? String).orEmpty()
            return GameMetadata(text("releaseDate"), text("genres"), text("developer"), text("publisher")).cleaned()
        }
    }
}

enum class ThemeStyle { M3, GLASS, MIUIX }

data class GlassSettings(val blur: Float = 12f, val refraction: Float = 16f, val opacity: Float = 0.65f) {
    fun clamped() = GlassSettings(
        if (blur.isFinite()) blur.coerceIn(0f, 30f) else 12f,
        if (refraction.isFinite()) refraction.coerceIn(0f, 28f) else 16f,
        if (opacity.isFinite()) opacity.coerceIn(0.4f, 0.95f) else 0.65f
    )
    fun toJson() = JSONObject().put("blur", blur).put("refraction", refraction).put("opacity", opacity).toString()
    companion object {
        fun decode(raw: String?) = runCatching {
            val json = JSONObject(raw ?: "{}")
            GlassSettings(json.optDouble("blur", 12.0).toFloat(), json.optDouble("refraction", 16.0).toFloat(), json.optDouble("opacity", 0.65).toFloat()).clamped()
        }.getOrDefault(GlassSettings())
    }
}
