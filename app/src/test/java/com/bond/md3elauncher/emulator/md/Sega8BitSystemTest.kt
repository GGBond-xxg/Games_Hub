package com.bond.md3elauncher.emulator.md

import com.bond.md3elauncher.data.GameItem
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.emulator.InternalEmulators
import com.bond.md3elauncher.system.*
import org.junit.Assert.*
import org.junit.Test

class Sega8BitSystemTest {
    @Test fun defaultsAndExplicitBindingsNeverHijackOtherPlatforms() {
        Sega8BitSystem.entries.forEach { system ->
            val platform = PlatformConfig(system.kind.name, system.kind)
            assertTrue(InternalEmulators.usesInternal(platform))
            assertTrue(InternalEmulators.usesInternal(platform.copy(emulatorPackage = system.internalPackage)))
            assertFalse(InternalEmulators.usesInternal(platform.copy(emulatorPackage = "com.retroarch")))
            assertFalse(InternalEmulators.usesInternal(platform.copy(emulatorPackage = InternalEmulators.MD_PACKAGE)))
            assertFalse(InternalEmulators.usesInternalMd(platform))
            assertEquals(system, Sega8BitSystem.fromId(system.kind.name))
        }
        assertNull(Sega8BitSystem.fromId("MD"))
        assertNull(Sega8BitSystem.fromId(null))
        assertTrue(InternalEmulators.usesInternalMd(PlatformConfig("MD", PlatformKind.MD)))
    }

    @Test fun formatsAndSaveRootsStaySeparateWhileCoreIsReused() {
        assertEquals(3, Sega8BitSystem.entries.map { it.storageDirectory }.distinct().size)
        Sega8BitSystem.entries.forEach { system ->
            assertNotEquals("internal_md", system.storageDirectory)
            assertEquals(".${system.extension}", system.romExtension("GAME.${system.extension.uppercase()}"))
            assertNull(system.romExtension("game.md"))
            Sega8BitSystem.entries.filter { it != system }.forEach {
                assertNull(system.romExtension("game.${it.extension}"))
                assertEquals("launch.check.sega8_format", LaunchPreflight.formatIssue(system.kind, it.extension, true))
            }
            assertNull(LaunchPreflight.formatIssue(system.kind, system.extension.uppercase(), true))
            assertNull(LaunchPreflight.formatIssue(system.kind, "zip", true))
            assertEquals("launch.check.archive", LaunchPreflight.formatIssue(system.kind, "7z", true))
            assertNull(LaunchPreflight.formatIssue(system.kind, "7z", false))
            listOf(true, false).forEach { gles3 ->
                assertEquals(LaunchPreflight.coreName(PlatformKind.MD, gles3), LaunchPreflight.coreName(system.kind, gles3))
            }
        }
    }

    @Test fun artworkAndMetadataUseTheirOwnPlatformCatalogues() {
        val tgdb = listOf("35", "20", "4949")
        val ss = listOf(2, 21, 109)
        val repos = listOf("Sega_-_Master_System_-_Mark_III", "Sega_-_Game_Gear", "Sega_-_SG-1000")
        Sega8BitSystem.entries.forEachIndexed { index, system ->
            assertEquals(tgdb[index], TheGamesDb.platform(system.kind.title))
            assertEquals(listOf(repos[index]), CoverSources.libretroRepos(system.kind.title))
            assertEquals(ss[index], GameScrapeSources.systemId(GameItem("qa", system.kind.name, system.kind.title,
                "QA", "qa.${system.extension}", system.extension, "content://qa", addedAt = 1)))
        }
    }
}
