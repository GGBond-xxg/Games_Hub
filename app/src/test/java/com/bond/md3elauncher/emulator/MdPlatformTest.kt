package com.bond.md3elauncher.emulator

import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdPlatformTest {
    @Test
    fun mdCartridgeExtensionsAreScannable() {
        assertTrue(PlatformKind.MD.extensions.containsAll(setOf("md", "gen", "smd", "bin", "zip")))
        assertFalse(PlatformKind.MD.extensions.contains("cue"))
    }

    @Test
    fun mdDefaultsToInternalButHonorsExternalSelection() {
        val defaultConfig = PlatformConfig(id = "MD", kind = PlatformKind.MD)
        val externalConfig = defaultConfig.copy(emulatorPackage = "com.explusalpha.MdEmu")

        assertTrue(InternalEmulators.usesInternalMd(defaultConfig))
        assertFalse(InternalEmulators.usesInternalMd(externalConfig))
    }
}
