package com.bond.md3elauncher.ui

import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import com.bond.md3elauncher.emulator.InternalEmulators
import com.bond.md3elauncher.system.LaunchPreflight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalPlatformsTest {
    @Test
    fun oldTabOrderKeepsExistingRelativeOrderAndAddsEachNewPlatformOnce() {
        val old = listOf("ANDROID", "PSP", "NS", "GBA", "GB", "SFC", "NES", "MD", "PS1", "N64", "ARCADE")
        val migrated = normalizedTabOrder(old)
        assertEquals(old, migrated.filter { it !in additionalEmulatorTabs }.map { it.name })
        assertEquals(additionalEmulatorTabs.toSet(), migrated.filter { it in additionalEmulatorTabs }.toSet())
        assertEquals(migrated.size, migrated.distinct().size)
        assertEquals(migrated, normalizedTabOrder(migrated.map { it.name }))
    }

    @Test
    fun additionalPlatformsNeverSelectAnInternalCoreByDefault() {
        additionalEmulatorTabs.forEach { tab ->
            val kind = PlatformKind.valueOf(tab.name)
            assertFalse(InternalEmulators.usesInternal(PlatformConfig(id = kind.name, kind = kind)))
            assertNull(LaunchPreflight.coreName(kind, gles3 = true))
            assertNull(LaunchPreflight.coreName(kind, gles3 = false))
        }
    }

    @Test
    fun discTrackSidecarsAreNotScannedAsDreamcastGames() {
        assertTrue(PlatformKind.DREAMCAST.extensions.containsAll(setOf("gdi", "chd", "cdi")))
        assertFalse("bin" in PlatformKind.DREAMCAST.extensions)
        assertFalse("raw" in PlatformKind.DREAMCAST.extensions)
        assertFalse("cue" in PlatformKind.DREAMCAST.extensions)
    }

    @Test
    fun platformSpecificFormatsStaySeparated() {
        assertTrue("nds" in PlatformKind.NDS.extensions)
        assertFalse("nds" in PlatformKind.N3DS.extensions)
        assertTrue("3ds" in PlatformKind.N3DS.extensions)
        assertTrue("chd" in PlatformKind.PS2.extensions)
        assertTrue("gcm" in PlatformKind.GC.extensions)
        assertTrue("wbfs" in PlatformKind.WII.extensions)
        assertFalse("wbfs" in PlatformKind.GC.extensions)
    }
}
