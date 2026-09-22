package com.bond.md3elauncher.io

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val folder = TemporaryFolder()

    private fun archive(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun extractsArtworkAndSaveSlotsWithoutChangingLiveData() {
        val stage = folder.newFolder("stage")
        val live = folder.newFile("existing.state").apply { writeText("original") }
        val zip = archive("manifest.json" to "{}", "files/internal_gba/game_data/game/slot_5.state" to "state", "files/custom_icons/test.png" to "image")
        BackupArchive.extract(ByteArrayInputStream(zip), stage)
        assertEquals("state", File(stage, "internal_gba/game_data/game/slot_5.state").readText())
        assertEquals("image", File(stage, "custom_icons/test.png").readText())
        assertEquals("original", live.readText())
    }

    @Test fun rejectsTraversalAbsolutePathsAndNonUserFiles() {
        val invalid = listOf("../outside", "/outside", "custom_icons/../../outside.png", "custom_icons\\outside.png",
            "internal_gba/system/bios.bin", "internal_gba/roms/game.gba", "internal_gba/game_data/game/slot_6.state",
            "internal_gba/game_data/game/cheat_reload.state", "custom_icons/C:outside.png", "shared_prefs/settings.xml")
        invalid.forEachIndexed { index, path ->
            assertFalse(path, BackupPaths.allowed(path))
            val zip = archive("manifest.json" to "{}", "files/$path" to "bad")
            assertThrows(IllegalArgumentException::class.java) { BackupArchive.extract(ByteArrayInputStream(zip), folder.newFolder("bad$index")) }
        }
    }

    @Test fun rejectsMissingManifestAndNonZipInput() {
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.extract(ByteArrayInputStream("not a zip".toByteArray()), folder.newFolder("text")) }
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.extract(ByteArrayInputStream(archive("files/custom_icons/test.png" to "image")), folder.newFolder("no-manifest")) }
    }

    @Test fun boundsActualExpandedBytesRatherThanTrustingZipMetadata() {
        val zip = archive("manifest.json" to "{}", "files/custom_icons/test.png" to "x".repeat(20000))
        assertThrows(IllegalArgumentException::class.java) { BackupArchive.extract(ByteArrayInputStream(zip), folder.newFolder("limit"), maxBytes = 1024) }
    }

    @Test fun allowsEveryRuntimeButOnlyFiveNormalSlotsAndQuickSave() {
        listOf("gba", "fc", "sfc", "md", "ps1", "n64", "arcade").forEach { runtime ->
            val prefix = "internal_$runtime/game_data/game/"
            (1..5).forEach { assertTrue(BackupPaths.allowed("${prefix}slot_$it.state")) }
            assertTrue(BackupPaths.allowed("${prefix}quick.state"))
            assertTrue(BackupPaths.allowed("${prefix}save_ram.srm"))
        }
    }
}
