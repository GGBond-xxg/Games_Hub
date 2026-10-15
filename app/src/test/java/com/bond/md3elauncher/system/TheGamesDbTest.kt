package com.bond.md3elauncher.system

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TheGamesDbTest {
    @Test fun englishAndroidArtworkSearchIsNotRestrictedToAnAndroidPlatform() {
        val requests = mutableListOf<String>()
        val client = TheGamesDb { url ->
            requests += java.net.URLDecoder.decode(url, "UTF-8")
            if (url.contains("ByGameName")) {
                JSONObject("""{"data":{"games":[{"id":104438,"game_title":"Incredibox","platform":1}]}}""")
            } else {
                JSONObject("""{"data":{"base_url":{"original":"https://cdn.thegamesdb.net/images/original/"},"images":{"104438":[
                    {"type":"boxart","side":"front","filename":"boxart/front/104438-1.jpg"},
                    {"type":"banner","side":null,"filename":"banner/104438-1.jpg"}]}}}""")
            }
        }
        val covers = client.searchArtwork(listOf("Incredibox"), "Android apps", "test-placeholder", CoverArtworkType.BOX_ART)
        assertEquals("Incredibox", covers.single().title)
        assertTrue(requests.first().contains("name=Incredibox"))
        assertFalse(requests.first().contains("filter[platform]"))
        assertTrue(requests.last().contains("filter[type]=boxart"))
        assertTrue(client.searchArtwork(listOf("incredibox"), "安卓应用", "test-placeholder", CoverArtworkType.SCREENSHOT).isEmpty())
        assertTrue(requests.last().contains("filter[type]=screenshot"))
    }
    private fun game(id: Int, title: String = "Advance Wars", platform: Int = 5, region: Int = 1) =
        JSONObject().put("id", id).put("game_title", title).put("platform", platform).put("region_id", region)

    @Test fun correctPlatformAndExactTitleAreRequiredForAutomaticMatching() {
        assertNull(TheGamesDb.exactGame(listOf(game(1, platform = 38)), listOf("Advance Wars"), "5"))
        assertNull(TheGamesDb.exactGame(listOf(game(1, title = "Advance Wars 2")), listOf("Advance Wars"), "5"))
        assertNull(TheGamesDb.exactGame(listOf(game(1, title = "Kirby: Nightmare in Dream Land")), listOf("星之卡比"), "5"))
        assertEquals("13", TheGamesDb.platform("PSP"))
        assertEquals("4971", TheGamesDb.platform("Switch"))
    }
    @Test fun regionalDuplicatesResolveToOneReleaseWithoutMatchingSequels() {
        val matches = listOf(game(2, region = 6), game(3, title = "Advance Wars 2"), game(1))
        assertEquals(1, TheGamesDb.exactGame(matches, listOf("Advance Wars (USA)"), "5")!!.getInt("id"))
    }
    @Test fun imagesRespectNullSidesAndSeparateScreenshotsFromBoxArt() {
        val json = JSONObject("""{"data":{"base_url":{"original":"https://cdn.thegamesdb.net/images/original/"},"images":{"1":[
            {"type":"boxart","side":"front","filename":"boxart/front/1.jpg"},
            {"type":"boxart","side":"back","filename":"boxart/back/1.jpg"},
            {"type":"screenshot","side":null,"filename":"screenshot/1.jpg"},
            {"type":"fanart","side":null,"filename":"fanart/1.jpg"}]}}}""")
        val names = mapOf("1" to "Example")
        assertEquals(listOf("https://cdn.thegamesdb.net/images/original/screenshot/1.jpg"),
            TheGamesDb.parseImages(json, names, CoverArtworkType.SCREENSHOT).map { it.imageUrl })
        assertEquals(1, TheGamesDb.parseImages(json, names, CoverArtworkType.BOX_ART).size)
        json.getJSONObject("data").getJSONObject("images").getJSONArray("1").remove(2)
        assertTrue(TheGamesDb.parseImages(json, names, CoverArtworkType.SCREENSHOT).isEmpty())
    }
    @Test fun dictionariesResolveNamesAndAreReusedAcrossTheBatch() {
        var calls = 0
        val client = TheGamesDb { url ->
            calls++
            val kind = url.substringAfter("/v1/").substringBefore('?').lowercase()
            JSONObject().put("data", JSONObject().put(kind, JSONObject().put("6", JSONObject().put("name", "Resolved $kind"))))
        }
        val game = game(1).put("release_date", "2001-09-10")
            .put("genres", org.json.JSONArray("[6]")).put("developers", org.json.JSONArray("[6]")).put("publishers", org.json.JSONArray("[6]"))
        repeat(2) {
            val metadata = client.metadata(game, "test-placeholder")
            assertEquals("2001-09-10", metadata.releaseDate)
            assertEquals("Resolved genres", metadata.genres)
            assertEquals("Resolved developers", metadata.developer)
            assertEquals("Resolved publishers", metadata.publisher)
        }
        assertEquals(3, calls)
    }
    @Test fun broadChineseSeriesDoesNotAcquireSpecificAutomaticAliases() {
        assertTrue(GameTitleQueries.exactAliases("星之卡比").isEmpty())
        assertTrue(GameTitleQueries.manual("星之卡比").contains("Kirby"))
        assertTrue(GameTitleQueries.exactAliases("高级战争").contains("Advance Wars"))
    }
    @Test fun authenticationFailuresAreNotConvertedToEmptyResults() {
        val client = TheGamesDb { throw ScrapeHttpFailure(403) }
        try { client.search("Kirby", "test-placeholder", null); fail("Expected authentication failure") }
        catch (failure: ScrapeHttpFailure) { assertEquals(403, failure.status); assertFalse(failure.message!!.contains("placeholder")) }
    }
}
