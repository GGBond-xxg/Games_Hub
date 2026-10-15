package com.bond.md3elauncher.system

import com.bond.md3elauncher.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GameScrapeSourcesTest {
    private fun response(extra: String = "", system: Int = 12) = JSONObject("""{"response":{"jeux":[{
        "id":7,"systeme":{"id":$system},"noms":[{"text":"Example Quest","region":"us"}],
        "dates":[{"text":"2002-04-05","region":"us"}],"developpeur":{"text":"Example Studio"},"editeur":{"text":"Example Publisher"},
        "genres":[{"noms":[{"langue":"en","text":"Adventure"}]}],
        "medias":[{"type":"box-2D","url":"https://example.org/cover.png"},{"type":"ss","url":"https://example.org/screen.png"}]
        }$extra]}}""")
    @Test fun exactPlatformMatchSeparatesInformationCoverAndScreenshot() {
        val value = GameScrapeSources.screenScraper(response(), "Example Quest (USA)", 12, "en")!!
        assertEquals("2002-04-05", value.metadata.releaseDate)
        assertEquals("Example Studio", value.metadata.developer)
        assertEquals("Adventure", value.metadata.genres)
        assertTrue(value.coverUrl!!.endsWith("cover.png")); assertTrue(value.screenshotUrl!!.endsWith("screen.png"))
    }
    @Test fun wrongPlatformAndFuzzyTitlesAreNeverAutomaticallyApplied() {
        assertNull(GameScrapeSources.screenScraper(response(system = 13), "Example Quest", 12, "en"))
        assertNull(GameScrapeSources.screenScraper(response(), "Example Quest 2", 12, "en"))
    }
    @Test fun ambiguousExactTitlesAreSkipped() {
        val extra = """,{"id":8,"systeme":{"id":12},"noms":[{"text":"Example Quest"}]}"""
        assertNull(GameScrapeSources.screenScraper(response(extra), "Example Quest", 12, "en"))
    }
    @Test fun missingScreenshotDoesNotReuseCover() {
        val json=response();json.getJSONObject("response").getJSONArray("jeux").getJSONObject(0).getJSONArray("medias").remove(1)
        assertNull(GameScrapeSources.screenScraper(json,"Example Quest",12,"en")!!.screenshotUrl)
    }
    @Test fun metadataRoundTripsAndOnlyFillsBlankFields() {
        val manual=GameMetadata(developer="My studio", genres="Action")
        val merged=manual.fillMissing(GameMetadata("2000", "Adventure", "Other", "Publisher"))
        assertEquals("My studio",merged.developer);assertEquals("Action",merged.genres);assertEquals("2000",merged.releaseDate)
        assertEquals(merged,GameMetadata.fromJson(merged.toJson()))
        assertTrue(GameMetadata.fromJson(null).isEmpty)
    }
    @Test fun needsRespectIndependentArtworkAndUserOverrides() {
        val game=GameItem("test","GBA","GBA","Example","Example.gba","gba","content://test",coverPath="embedded.png")
        val need=ScrapeNeeds.forGame(game,ItemOverride("test",previewImagePath="manual.png",metadata=GameMetadata("2000","Action","Studio","Publisher")))
        assertFalse(need.any)
        assertTrue(ScrapeNeeds.forGame(game,null).screenshot)
        assertFalse(ScrapeNeeds.forGame(game,null).cover)
    }
    @Test fun glassSettingsClampMalformedBackupValues() {
        val value=GlassSettings.decode("""{"blur":900,"refraction":-1,"opacity":0}""")
        assertEquals(30f,value.blur);assertEquals(0f,value.refraction);assertEquals(0.4f,value.opacity)
        assertEquals(GlassSettings(),GlassSettings.decode("broken"))
    }
}
