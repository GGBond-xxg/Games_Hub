package com.bond.md3elauncher.io

import org.junit.Assert.assertEquals
import org.junit.Test

class RomScannerTitleTest {
    @Test
    fun removesNoIntroPrefixAndRegionTags() {
        assertEquals(
            "Pokemon Emerald",
            cleanRomTitle("2171 - Pokemon_Emerald (USA, Europe) [Rev 1].gba")
        )
    }

    @Test
    fun decodesUriTextAndCjkTags() {
        assertEquals(
            "星之卡比 镜之大迷宫",
            cleanRomTitle("%E6%98%9F%E4%B9%8B%E5%8D%A1%E6%AF%94_%E9%95%9C%E4%B9%8B%E5%A4%A7%E8%BF%B7%E5%AE%AB%E3%80%90%E6%B1%89%E5%8C%96%E3%80%91.gba")
        )
    }

    @Test
    fun keepsReadableNameWhenNoExtensionExists() {
        assertEquals("Chrono Trigger", cleanRomTitle("Chrono Trigger"))
    }

    @Test
    fun keepsLiteralPlusCharacters() {
        assertEquals("Game+Plus", cleanRomTitle("Game+Plus.gba"))
    }
}
