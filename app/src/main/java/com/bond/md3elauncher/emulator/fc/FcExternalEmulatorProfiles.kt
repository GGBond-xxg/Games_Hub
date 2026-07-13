package com.bond.md3elauncher.emulator.fc

/**
 * FC/NES now has an internal FCEUmm Phase 1, while external emulator launch
 * remains available as an alternative.
 *
 * This object keeps FC/NES external package names, search keywords and
 * RetroArch core names out of the launcher UI code.
 */
object FcExternalEmulatorProfiles {
    val retroArchCoreNames: List<String> = listOf(
        "fceumm_libretro_android.so",
        "nestopia_libretro_android.so",
        "quicknes_libretro_android.so",
        "mesen_libretro_android.so"
    )

    val recommendedKeywords: List<String> = listOf(
        "nes.emu",
        "nesemu",
        "com.explusalpha.nesemu",
        "nostalgia.nes",
        "nostalgiaemulators",
        "fceumm",
        "nestopia",
        "quicknes",
        "mesen",
        "retroarch",
        "com.retroarch",
        "fc emulator",
        "nes emulator",
        "famicom"
    )

    fun matchesPackageOrLabel(label: String, packageName: String): Boolean {
        val text = (label + " " + packageName).lowercase()
        return recommendedKeywords.any { it in text }
    }

    fun matchesPackage(packageName: String): Boolean {
        val value = packageName.lowercase()
        // Keep John NESS here so a user can still manually select it from “All” and get the
        // guarded launch path / warning, but do not include it in recommendedKeywords.
        return value.contains("nes.emu") ||
            value.contains("nesemu") ||
            value == "com.explusalpha.nesemu" ||
            value.contains("nostalgia") && value.contains("nes") ||
            value.contains("johnness") ||
            value.contains("johnnes") ||
            value.contains("johnemulators") && value.contains("ness") ||
            value.contains("johnemulators") && value.contains("nes") ||
            value.contains("fceumm") ||
            value.contains("nestopia") ||
            value.contains("quicknes") ||
            value.contains("mesen") ||
            value.contains("famicom")
    }
}
