package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.PlatformKind
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoverSourcesTest {
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
