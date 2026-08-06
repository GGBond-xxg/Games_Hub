package com.bond.md3elauncher.emulator

import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadePlatformTest {
    @Test
    fun arcadeScannerOnlyAdvertisesCompleteZipRomsets() {
        assertEquals(setOf("zip"), PlatformKind.ARCADE.extensions)
    }

    @Test
    fun defaultArcadePlatformUsesBuiltInCore() {
        val defaultConfig = PlatformConfig(id = "ARCADE", kind = PlatformKind.ARCADE)
        assertTrue(InternalEmulators.usesInternalArcade(defaultConfig))
    }
}
