package com.bond.md3elauncher.system

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in live smoke test. Supply a disposable key through the environment, never a source file. */
class TheGamesDbLiveTest {
    @Test fun chineseEnglishMetadataCoversAndScreenshots() {
        val key = System.getenv("GAMEHUB_TGDB_TEST_KEY").orEmpty()
        assumeTrue("Live API key not supplied", key.isNotBlank())
        val client = TheGamesDb()
        for (query in listOf("星之卡比", "Kirby", "Advance Wars", "Dante's Inferno")) {
            val games = client.search(query, key, if (query == "Dante's Inferno") "13" else null)
            assertTrue("No candidates for $query", games.isNotEmpty())
        }
        val games = client.search("Advance Wars", key, "5")
        val match = TheGamesDb.exactGame(games, listOf("Advance Wars"), "5")!!
        val metadata = client.metadata(match, key)
        assertEquals("2001-09-10", metadata.releaseDate)
        assertTrue(metadata.developer.startsWith("Intelligent Systems"))
        assertTrue(metadata.isComplete)
        for (type in CoverArtworkType.entries) {
            val images = client.images(listOf(match), key, type)
            assertTrue("No images for $type", images.isNotEmpty())
            val connection = java.net.URL(images.first().imageUrl).openConnection() as java.net.HttpURLConnection
            try {
                connection.connectTimeout = 9000; connection.readTimeout = 9000
                assertEquals(200, connection.responseCode)
                connection.inputStream.use { assertNotNull(javax.imageio.ImageIO.read(it)) }
            } finally { connection.disconnect() }
        }
    }
}
