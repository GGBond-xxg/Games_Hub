package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.PlatformKind
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoverSourcesTest {
    @Test fun separatesScreenshotsFromBoxArtAndEncodesTheFileName() {
        val repo = "Nintendo_-_Game_Boy_Advance"
        val prefix = "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/"
        assertEquals(prefix + "Named_Snaps/Example%20%28USA%29.png", CoverSources.libretroImageUrl(repo, "Example (USA)", CoverArtworkType.SCREENSHOT))
        assertEquals(prefix + "Named_Boxarts/Example%20%28USA%29.png", CoverSources.libretroImageUrl(repo, "Example (USA)", CoverArtworkType.BOX_ART))
        assertEquals(prefix + "Named_Snaps/A%2FB%20%26%20C.png", CoverSources.libretroImageUrl(repo, "A/B & C", CoverArtworkType.SCREENSHOT))
    }

    @Test fun mapsEverySupportedRetroPlatform() {
        PlatformKind.entries.filter { it != PlatformKind.SWITCH }.forEach { assertTrue(it.title, CoverSources.libretroRepos(it.title).isNotEmpty()) }
        assertTrue(CoverSources.libretroRepos("Switch").isEmpty())
    }
    @Test fun extractsOnlySecureCoverMediaAndToleratesMalformedRows() {
        val json = JSONObject("""{"response":{"jeux":[{"noms":[{"text":"Example"}],"medias":[{"type":"box-2D","url":"https://example.test/front.png"},{"type":"video","url":"https://example.test/movie.mp4"},{"type":"box-2D","url":"http://example.test/plain.png"},null]},{}]}}""")
        val covers = CoverSources.screenScraper(json, "fallback")
        assertEquals(1, covers.size)
        assertEquals("Example", covers.single().title)
        assertTrue(CoverSources.screenScraper(JSONObject(), "fallback").isEmpty())
    }
}
