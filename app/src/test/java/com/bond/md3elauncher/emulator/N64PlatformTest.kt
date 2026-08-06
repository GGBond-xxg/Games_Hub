package com.bond.md3elauncher.emulator

import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind
import org.junit.Assert.assertTrue
import org.junit.Test

class N64PlatformTest {
    @Test
    fun platformRecognizesSupportedCartridgeFormatsAndArchives() {
        assertTrue(PlatformKind.N64.extensions.containsAll(setOf("n64", "v64", "z64", "bin", "zip", "7z")))
    }

    @Test
    fun defaultPlatformUsesBuiltInN64Core() {
        val defaultConfig = PlatformConfig(id = "N64", kind = PlatformKind.N64)
        assertTrue(InternalEmulators.usesInternalN64(defaultConfig))
    }
}
