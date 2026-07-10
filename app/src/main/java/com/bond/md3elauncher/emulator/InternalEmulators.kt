package com.bond.md3elauncher.emulator
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind

object InternalEmulators {
    const val GBA_PACKAGE = "internal:gba"
    const val GBA_NAME = "内置 GBA 模拟器"

    fun isInternalGbaPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == GBA_PACKAGE

    fun usesInternalGba(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.GBA && isInternalGbaPackage(platform.emulatorPackage)
}
