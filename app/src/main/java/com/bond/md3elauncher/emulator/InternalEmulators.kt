package com.bond.md3elauncher.emulator
import com.bond.md3elauncher.data.PlatformConfig
import com.bond.md3elauncher.data.PlatformKind

object InternalEmulators {
    const val GBA_PACKAGE = "internal:gba"
    const val GBA_NAME = "内置 GBA 模拟器"

    const val GB_PACKAGE = "internal:gbc"
    const val GB_NAME = "内置 GB/GBC 模拟器"

    const val FC_PACKAGE = "internal:fc"
    const val FC_NAME = "内置 FC/NES 模拟器"

    fun isInternalGbaPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == GBA_PACKAGE

    fun isInternalFcPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == FC_PACKAGE

    fun isInternalGbPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == GB_PACKAGE || packageName == GBA_PACKAGE

    fun usesInternalGba(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.GBA && isInternalGbaPackage(platform.emulatorPackage)

    fun usesInternalGb(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.GB && isInternalGbPackage(platform.emulatorPackage)

    fun usesInternalGbaCore(platform: PlatformConfig): Boolean =
        usesInternalGba(platform) || usesInternalGb(platform)

    fun usesInternalFc(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.NES && isInternalFcPackage(platform.emulatorPackage)

    fun usesInternal(platform: PlatformConfig): Boolean =
        usesInternalGbaCore(platform) || usesInternalFc(platform)
}

