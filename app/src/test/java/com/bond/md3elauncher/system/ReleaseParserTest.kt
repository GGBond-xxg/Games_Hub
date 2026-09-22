package com.bond.md3elauncher.system

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseParserTest {
    private fun release(name: String = "GameHub-v1.1.0-arm64.apk", url: String = "https://github.com/${ReleaseParser.REPO}/releases/download/v1.1.0/$name", digest: String = "sha256:" + "a".repeat(64)): String =
        JSONObject().put("tag_name", "v1.1.0").put("assets", JSONArray().put(JSONObject()
            .put("name", name).put("browser_download_url", url).put("size", 123).put("state", "uploaded").put("digest", digest))).toString()

    @Test fun comparesNumericVersionsAndRejectsPrereleases() {
        assertTrue(ReleaseParser.newer("v1.10.0", "1.9.9"))
        assertFalse(ReleaseParser.newer("v1.1.0", "1.1.0"))
        assertFalse(ReleaseParser.newer("1.1.0-beta", "1.0.2"))
        assertFalse(ReleaseParser.newer("1.0.2", "1.1.0"))
    }
    @Test fun selectsNativeAbiWithVerifiedMetadata() {
        assertNotNull(ReleaseParser.parse(release(), listOf("arm64-v8a", "armeabi-v7a")).asset)
        assertNull(ReleaseParser.parse(release(), listOf("armeabi-v7a")).asset)
        assertNotNull(ReleaseParser.parse(release("GameHub-v1.1.0-arm32.apk"), listOf("armeabi-v7a")).asset)
        assertNull(ReleaseParser.parse(release(), listOf("x86_64")).asset)
    }
    @Test fun rejectsUnverifiedOrForeignAssets() {
        assertNull(ReleaseParser.parse(release(digest = ""), listOf("arm64-v8a")).asset)
        assertNull(ReleaseParser.parse(release(url = "https://github.com.evil.test/update.apk"), listOf("arm64-v8a")).asset)
        assertNull(ReleaseParser.parse(release(name = "GameHub-v1.0.2-arm64.apk"), listOf("arm64-v8a")).asset)
    }
}
