package com.bond.md3elauncher.data

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class GameLibraryCodecTest {
    private val game = GameItem("one", "gba", "GBA", "Example", "example.gba", "gba", "content://roms/one", 123L, "SERIAL", "/files/cover.png", "/files/grid.png")
    private val valid = GameLibraryCodec.encode(listOf(game))
    @Test fun preservesArtworkAndMetadata() { assertEquals(game, GameLibraryCodec.decode(valid)!!.games.single()) }
    @Test fun keepsGoodRecordsWhenOthersAreMalformedOrDuplicate() {
        val mixed = JSONArray(valid).put("broken").put(JSONArray(valid).getJSONObject(0)).toString()
        val result = GameLibraryCodec.recover(mixed, null)
        assertEquals(listOf(game), result.games)
        assertEquals(2, result.rejected)
    }
    @Test fun restoresPreviousLibraryIfWholeDocumentIsBroken() {
        assertEquals(listOf(game), GameLibraryCodec.recover("[broken", valid).games)
        assertEquals(listOf(game), GameLibraryCodec.recover("[{}]", valid).games)
    }
    @Test fun intentionalEmptyLibraryDoesNotResurrectDeletedGames() {
        assertTrue(GameLibraryCodec.recover("[]", valid).games.isEmpty())
        assertTrue(GameLibraryCodec.recover(null, valid).games.isEmpty())
    }
}
