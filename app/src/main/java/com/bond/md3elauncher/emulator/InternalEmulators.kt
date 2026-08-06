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

    const val SFC_PACKAGE = "internal:sfc"
    const val SFC_NAME = "内置 SFC/SNES 模拟器"

    const val MD_PACKAGE = "internal:md"
    const val MD_NAME = "内置 MD/Genesis 模拟器"

    const val PS1_PACKAGE = "internal:ps1"
    const val PS1_NAME = "内置 PS1 模拟器"

    const val N64_PACKAGE = "internal:n64"
    const val N64_NAME = "N64"

    const val ARCADE_PACKAGE = "internal:arcade"
    const val ARCADE_NAME = "Arcade"

    fun isInternalGbaPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == GBA_PACKAGE

    fun isInternalFcPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == FC_PACKAGE

    fun isInternalSfcPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == SFC_PACKAGE

    fun isInternalMdPackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == MD_PACKAGE

    fun isInternalPs1Package(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == PS1_PACKAGE

    fun isInternalN64Package(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == N64_PACKAGE

    fun isInternalArcadePackage(packageName: String?): Boolean =
        packageName.isNullOrBlank() || packageName == ARCADE_PACKAGE

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

    fun usesInternalSfc(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.SFC && isInternalSfcPackage(platform.emulatorPackage)

    fun usesInternalMd(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.MD && isInternalMdPackage(platform.emulatorPackage)

    fun usesInternalPs1(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.PS1 && isInternalPs1Package(platform.emulatorPackage)

    fun usesInternalN64(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.N64 && isInternalN64Package(platform.emulatorPackage)

    fun usesInternalArcade(platform: PlatformConfig): Boolean =
        platform.kind == PlatformKind.ARCADE && isInternalArcadePackage(platform.emulatorPackage)

    fun usesInternal(platform: PlatformConfig): Boolean =
        usesInternalGbaCore(platform) || usesInternalFc(platform) || usesInternalSfc(platform) || usesInternalMd(platform) || usesInternalPs1(platform) || usesInternalN64(platform) || usesInternalArcade(platform)
}
