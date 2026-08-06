package com.bond.md3elauncher.emulator

import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ps1PlatformTest {
    @Test
    fun ps1FirstPhaseOnlyScansSingleFileImages() {
        assertTrue(PlatformKind.PS1.extensions.containsAll(setOf("chd", "pbp", "iso", "bin")))
        assertFalse(PlatformKind.PS1.extensions.contains("cue"))
        assertFalse(PlatformKind.PS1.extensions.contains("m3u"))
    }

    @Test
    fun ps1DefaultsToInternalButHonorsExternalSelection() {
        val defaultConfig = PlatformConfig(id = "PS1", kind = PlatformKind.PS1)
        val externalConfig = defaultConfig.copy(emulatorPackage = "com.github.stenzek.duckstation")

        assertTrue(InternalEmulators.usesInternalPs1(defaultConfig))
        assertFalse(InternalEmulators.usesInternalPs1(externalConfig))
    }
}
