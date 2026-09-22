package com.bond.md3elauncher.system

import org.json.JSONObject

internal data class ReleaseInfo(val version: String, val notes: String, val page: String, val asset: ReleaseAsset?)
internal data class ReleaseAsset(val name: String, val url: String, val size: Long, val sha256: String)

internal object ReleaseParser {
    const val REPO = "GGBond-xxg/Games_Hub"
    const val PAGE = "https://github.com/$REPO/releases/latest"
    private fun parts(version: String): List<Int>? {
        val clean = version.removePrefix("v")
        if (!clean.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) return null
        return clean.split('.').map { it.toIntOrNull() ?: return null }
    }
    fun newer(candidate: String, installed: String): Boolean {
        val next = parts(candidate) ?: return false
        val current = parts(installed) ?: return false
        for (i in next.indices) if (next[i] != current[i]) return next[i] > current[i]
        return false
    }
    fun parse(raw: String, abis: List<String>): ReleaseInfo {
        val json = JSONObject(raw)
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease"))
        val version = json.getString("tag_name")
        require(parts(version) != null)
        val suffix = when {
            "arm64-v8a" in abis -> "arm64.apk"
            "armeabi-v7a" in abis -> "arm32.apk"
            else -> null
        }
        val assets = json.getJSONArray("assets")
        val expectedName = "GameHub-v${version.removePrefix("v")}-$suffix"
        val selected = (0 until assets.length()).mapNotNull { i ->
            val asset = assets.getJSONObject(i)
            val url = asset.optString("browser_download_url")
            val size = asset.optLong("size")
            val digest = asset.optString("digest").removePrefix("sha256:")
            if (suffix == null || asset.optString("name") != expectedName || asset.optString("state") != "uploaded" ||
                !url.startsWith("https://github.com/$REPO/releases/download/$version/") ||
                size !in 1..(256L * 1024 * 1024) || !digest.matches(Regex("[a-fA-F0-9]{64}"))) null
            else ReleaseAsset(expectedName, url, size, digest.lowercase())
        }.firstOrNull()
        return ReleaseInfo(version, json.optString("body").take(12000), "https://github.com/$REPO/releases/tag/$version", selected)
    }
}
