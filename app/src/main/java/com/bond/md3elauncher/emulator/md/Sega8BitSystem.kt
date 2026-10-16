package com.bond.md3elauncher.emulator.md

import com.bond.md3elauncher.data.PlatformKind
import java.util.Locale

/** Explicit system selection: sharing a core must not share MD controls or saves. */
internal enum class Sega8BitSystem(
    val kind: PlatformKind,
    val extension: String,
    val coreSystem: String,
    val storageDirectory: String,
    val internalPackage: String
) {
    SMS(PlatformKind.SMS, "sms", "master system", "internal_sms", "internal:sms"),
    GG(PlatformKind.GG, "gg", "game gear", "internal_gg", "internal:gg"),
    SG1000(PlatformKind.SG1000, "sg", "sg-1000", "internal_sg1000", "internal:sg1000");

    fun romExtension(fileName: String): String? =
        ".$extension".takeIf { fileName.lowercase(Locale.ROOT).endsWith(it) }

    companion object {
        const val EXTRA_SYSTEM = "sega_8bit_system"
        fun fromKind(kind: PlatformKind): Sega8BitSystem? = entries.firstOrNull { it.kind == kind }
        fun fromId(id: String?): Sega8BitSystem? = entries.firstOrNull { it.kind.name == id }
    }
}
